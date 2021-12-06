package xyz.xenondevs.nova.tileentity

import de.studiocode.invui.gui.GUI
import de.studiocode.invui.virtualinventory.VirtualInventory
import de.studiocode.invui.virtualinventory.event.ItemUpdateEvent
import de.studiocode.invui.virtualinventory.event.UpdateReason
import de.studiocode.invui.window.impl.single.SimpleWindow
import net.md_5.bungee.api.chat.TranslatableComponent
import net.minecraft.world.entity.EquipmentSlot
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.data.database.entity.DaoTileEntity
import xyz.xenondevs.nova.data.serialization.DataHolder
import xyz.xenondevs.nova.data.serialization.cbf.element.CompoundElement
import xyz.xenondevs.nova.material.NovaMaterial
import xyz.xenondevs.nova.tileentity.network.energy.EnergyConnectionType
import xyz.xenondevs.nova.tileentity.network.item.ItemConnectionType
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.util.*
import xyz.xenondevs.nova.world.armorstand.FakeArmorStand
import xyz.xenondevs.nova.world.armorstand.FakeArmorStandManager
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.world.region.Region
import java.util.*

internal val SELF_UPDATE_REASON = object : UpdateReason {}

abstract class TileEntity(
    val uuid: UUID,
    data: CompoundElement,
    val material: NovaMaterial,
    val ownerUUID: UUID,
    val armorStand: FakeArmorStand,
) : DataHolder(data, true) {
    
    abstract val gui: Lazy<TileEntityGUI>?
    
    @Volatile
    var isValid: Boolean = true
        private set
    
    val location = armorStand.location.blockLocation
    val world = location.world!!
    val chunk = location.chunk
    val chunkPos = chunk.pos
    val facing = armorStand.location.facing
    
    private val inventories = ArrayList<VirtualInventory>()
    private val multiModels = ArrayList<MultiModel>()
    private val particleTasks = ArrayList<TileEntityParticleTask>()
    
    val additionalHitboxes = HashSet<Location>()
    
    var hasHitboxBeenPlaced = false
        private set
    
    /**
     * Called when the TileEntity is being broken.
     *
     * @param dropItems If items should be dropped. Can be ignored for reasons like
     * dropping the contents of a chest event when the player is in creative mode.
     *
     * @return A list of [ItemStack]s that should be dropped.
     */
    open fun destroy(dropItems: Boolean): ArrayList<ItemStack> {
        val drops = ArrayList<ItemStack>()
        if (dropItems) {
            saveData()
            val item = material.createItemBuilder(this).get()
            if (globalData.isNotEmpty()) item.setTileEntityData(globalData)
            if (this is Upgradable) drops += this.upgradeHolder.dropUpgrades()
            drops += item
        }
        
        // inventory drops ignore the dropItems parameter
        inventories.forEach { drops += it.items.filterNotNull() }
        TileInventoryManager.remove(uuid, inventories)
        
        return drops
    }
    
    /**
     * Called to save all data using the [storeData] method.
     */
    open fun saveData() {
        if (this is Upgradable)
            upgradeHolder.save(data)
    }
    
    /**
     * Called to get the [ItemStack] to be placed as the head of the [FakeArmorStand].
     */
    open fun getHeadStack(): ItemStack {
        return material.block!!.createItemStack()
    }
    
    /**
     * Calls the [getHeadStack] function and puts the result on the [FakeArmorStand].
     */
    fun updateHeadStack() {
        armorStand.setEquipment(EquipmentSlot.HEAD, getHeadStack())
        armorStand.updateEquipment()
    }
    
    /**
     * Called every tick for every TileEntity that is in a loaded chunk.
     */
    open fun handleTick() = Unit
    
    /**
     * Called asynchronously for every tick that this TileEntity is in a loaded chunk.
     */
    open fun handleAsyncTick() = Unit
    
    /**
     * Called after the TileEntity has been initialized and added to the
     * TileEntity map in the TileEntityManager.
     *
     * The [first] parameter specifies if it is the first time this
     * [TileEntity] got initialized, meaning it has just been placed.
     */
    abstract fun handleInitialized(first: Boolean)
    
    /**
     * Called after the hitbox block has been placed.
     * This action happens one tick after [handleInitialized] with first: true.
     */
    open fun handleHitboxPlaced() {
        hasHitboxBeenPlaced = true
    }
    
    /**
     * Called after the TileEntity has been removed from the
     * TileEntityManager's TileEntity map because it either got
     * unloaded or destroyed.
     *
     * @param unload If the [TileEntity] was removed because the chunk got unloaded.
     */
    open fun handleRemoved(unload: Boolean) {
        isValid = false
        if (gui?.isInitialized() == true) gui!!.value.closeWindows()
        
        armorStand.remove()
        multiModels.forEach { it.close() }
        particleTasks.forEach { it.stop() }
    }
    
    /**
     * Called when a player right-clicks the TileEntity.
     *
     * Only called once and always for the main hand.
     * Use [PlayerInteractEvent.handItems] to check both items.
     *
     * The event has should be cancelled if any action
     * is performed in that method.
     */
    open fun handleRightClick(event: PlayerInteractEvent) {
        if (gui != null && !event.player.hasInventoryOpen) {
            event.isCancelled = true
            gui!!.value.openWindow(event.player)
        }
    }
    
    /**
     * Gets a [VirtualInventory] for this [TileEntity].
     * When [dropItems] is true, the [VirtualInventory] will automatically be
     * deleted and its contents dropped when the [TileEntity] is destroyed.
     */
    fun getInventory(
        salt: String,
        size: Int,
        stackSizes: IntArray,
        itemHandler: (ItemUpdateEvent) -> Unit
    ): VirtualInventory {
        val inventory = TileInventoryManager.getOrCreate(
            uuid, uuid.salt(salt), size, arrayOfNulls(size), stackSizes
        )
        inventory.setItemUpdateHandler(itemHandler)
        inventories += inventory
        return inventory
    }
    
    /**
     * Gets a [VirtualInventory] for this [TileEntity].
     * When [dropItems] is true, the [VirtualInventory] will automatically be
     * deleted and its contents dropped when the [TileEntity] is destroyed.
     */
    fun getInventory(salt: String, size: Int, itemHandler: (ItemUpdateEvent) -> Unit) =
        getInventory(salt, size, IntArray(size) { 64 }, itemHandler)
    
    /**
     * Creates a new [MultiModel] for this [TileEntity].
     * When the [TileEntity] is removed, all [Model]s belonging
     * to this [MultiModel] will be removed.
     */
    fun createMultiModel(): MultiModel {
        return MultiModel().also(multiModels::add)
    }
    
    /**
     * Creates a new [TileEntityParticleTask] for this [TileEntity].
     * When the [TileEntity] is removed, the [TileEntityParticleTask]
     * will automatically be stopped as well.
     */
    fun createParticleTask(particles: List<Any>, tickDelay: Int): TileEntityParticleTask {
        val task = TileEntityParticleTask(this, particles, tickDelay)
        particleTasks += task
        return task
    }
    
    /**
     * Plays a sound effect to all viewers of this [TileEntity]
     */
    fun playSoundEffect(sound: Sound, volume: Float, pitch: Float) {
        getViewers().forEach {
            it.playSound(location, sound, volume, pitch)
        }
    }
    
    /**
     * Plays a sound effect to all viewers of this [TileEntity]
     */
    fun playSoundEffect(sound: String, volume: Float, pitch: Float) {
        getViewers().forEach {
            it.playSound(location, sound, volume, pitch)
        }
    }
    
    /**
     * Gets a [List] of all [players][Player] that this [TileEntity] is
     * visible for.
     */
    fun getViewers(): List<Player> =
        FakeArmorStandManager.getViewersOf(chunkPos)
    
    /**
     * Gets the correct direction a block side.
     */
    fun getFace(blockSide: BlockSide): BlockFace =
        blockSide.getBlockFace(armorStand.location.yaw)
    
    /**
     * Creates an energy side config
     */
    fun createEnergySideConfig(
        default: EnergyConnectionType,
        vararg blocked: BlockSide
    ): EnumMap<BlockFace, EnergyConnectionType> {
        
        val blockedFaces = blocked.map(::getFace)
        return CUBE_FACES.associateWithTo(enumMapOf()) {
            if (it in blockedFaces) EnergyConnectionType.NONE else default
        }
    }
    
    /**
     * Creates an energy side config
     */
    fun createExclusiveEnergySideConfig(
        type: EnergyConnectionType,
        vararg sides: BlockSide
    ): EnumMap<BlockFace, EnergyConnectionType> {
        
        val sideFaces = sides.map(::getFace)
        return CUBE_FACES.associateWithTo(emptyEnumMap()) {
            if (it in sideFaces) type else EnergyConnectionType.NONE
        }
    }
    
    /**
     * Creates an item side config
     */
    fun createItemSideConfig(
        default: ItemConnectionType,
        vararg blocked: BlockSide
    ): EnumMap<BlockFace, ItemConnectionType> {
        
        val sideConfig = EnumMap<BlockFace, ItemConnectionType>(BlockFace::class.java)
        val blockedFaces = blocked.map { getFace(it) }
        CUBE_FACES.forEach { sideConfig[it] = if (blockedFaces.contains(it)) ItemConnectionType.NONE else default }
        
        return sideConfig
    }
    
    /**
     * Creates a [Region] of a specified [size] that surrounds this [TileEntity].
     */
    fun getSurroundingRegion(size: Int): Region {
        val d = size + 0.5
        return Region(
            location.clone().center().subtract(d, d, d),
            location.clone().center().add(d, d, d)
        )
    }
    
    /**
     * Creates a block [Region] with the given [length], [width], [height] and
     * [vertical translation][translateVertical] in front of this [TileEntity].
     */
    fun getBlockFrontRegion(length: Int, width: Int, height: Int, translateVertical: Int): Region {
        return getFrontRegion(length * 2.0 + 1, width + 0.5, width + 0.5, height.toDouble(), translateVertical.toDouble())
    }
    
    /**
     * Creates a [Region] with the  given [length], [width], [height] and
     * [vertical translation][translateVertical] in front of this [TileEntity].
     */
    fun getFrontRegion(length: Double, width: Double, height: Double, translateVertical: Double): Region {
        return getFrontRegion(length, width / 2.0, width / 2.0, height, translateVertical)
    }
    
    /**
     * Creates a [Region] with the  given [length], [left] and [right] movement, [height] and
     * [vertical translation][translateVertical] in front of this [TileEntity].
     */
    fun getFrontRegion(length: Double, left: Double, right: Double, height: Double, translateVertical: Double): Region {
        val frontFace = getFace(BlockSide.FRONT)
        val startLocation = location.clone().center().advance(frontFace, 0.5)
        
        val pos1 = startLocation.clone().apply {
            advance(getFace(BlockSide.LEFT), left)
            y += translateVertical
        }
        
        val pos2 = startLocation.clone().apply {
            advance(getFace(BlockSide.RIGHT), right)
            advance(frontFace, length)
            y += height + translateVertical
        }
        
        return Region(LocationUtils.sort(pos1, pos2))
    }
    
    /**
     * Places additional hitboxes for this [TileEntity] and registers them
     * in the [TileEntityManager].
     */
    fun setAdditionalHitboxes(placeBlocks: Boolean, hitboxes: List<Location>) {
        if (placeBlocks) hitboxes.forEach { it.block.type = material.hitboxType!! }
        
        additionalHitboxes += hitboxes
        TileEntityManager.addTileEntityHitboxLocations(this, hitboxes)
    }
    
    override fun equals(other: Any?): Boolean {
        return other is TileEntity && other === this
    }
    
    override fun hashCode(): Int {
        return uuid.hashCode()
    }
    
    override fun toString(): String {
        return "${javaClass.name}(Material: $material, Location: ${armorStand.location.blockLocation}, UUID: $uuid)"
    }
    
    companion object {
        
        fun create(tileEntity: DaoTileEntity, location: Location): TileEntity {
            return create(
                tileEntity.id.value,
                location.clone().apply { center(); yaw = tileEntity.yaw },
                tileEntity.type,
                tileEntity.data,
                tileEntity.owner
            )
        }
        
        fun create(
            uuid: UUID,
            armorStandLocation: Location,
            material: NovaMaterial,
            data: CompoundElement,
            ownerUUID: UUID
        ): TileEntity {
            // create the fake armor stand
            val armorStand = FakeArmorStand(armorStandLocation, false) {
                it.isInvisible = true
                it.isMarker = true
                it.setSharedFlagOnFire(material.hitboxType.requiresLight)
            }
            
            // create the tile entity
            val tileEntity = material.tileEntityConstructor!!(uuid, data, material, ownerUUID, armorStand)
            
            // set the head stack and register
            armorStand.setEquipment(EquipmentSlot.HEAD, tileEntity.getHeadStack())
            armorStand.register()
            
            return tileEntity
        }
        
    }
    
}


abstract class TileEntityGUI(private val title: String) {
    
    /**
     * The main [GUI] of a [TileEntity] to be opened when it is right-clicked and closed when
     * the owning [TileEntity] is destroyed.
     */
    abstract val gui: GUI
    
    /**
     * A list of [GUIs][GUI] that are not a part of [gui] but should still be closed
     * when the [TileEntity] is destroyed.
     */
    val subGUIs = ArrayList<GUI>()
    
    /**
     * Opens a Window of the [gui] to the specified [player].
     */
    fun openWindow(player: Player) = SimpleWindow(player, arrayOf(TranslatableComponent(title)), gui).show()
    
    /**
     * Closes all Windows connected to this [TileEntityGUI].
     */
    fun closeWindows() {
        gui.closeForAllViewers()
        subGUIs.forEach(GUI::closeForAllViewers)
    }
    
}

