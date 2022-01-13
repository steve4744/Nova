package xyz.xenondevs.nova.tileentity.impl.energy

import org.bukkit.Axis
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Orientable
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.NOVA
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.serialization.cbf.element.CompoundElement
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.material.NovaMaterial
import xyz.xenondevs.nova.material.NovaMaterialRegistry
import xyz.xenondevs.nova.tileentity.Model
import xyz.xenondevs.nova.tileentity.TileEntity
import xyz.xenondevs.nova.tileentity.network.*
import xyz.xenondevs.nova.tileentity.network.NetworkType.FLUID
import xyz.xenondevs.nova.tileentity.network.NetworkType.ITEMS
import xyz.xenondevs.nova.tileentity.network.energy.EnergyBridge
import xyz.xenondevs.nova.tileentity.network.fluid.FluidBridge
import xyz.xenondevs.nova.tileentity.network.fluid.holder.FluidHolder
import xyz.xenondevs.nova.tileentity.network.item.ItemBridge
import xyz.xenondevs.nova.tileentity.network.item.holder.ItemHolder
import xyz.xenondevs.nova.ui.config.cable.CableConfigGUI
import xyz.xenondevs.nova.util.*
import xyz.xenondevs.nova.world.armorstand.FakeArmorStand
import xyz.xenondevs.nova.world.hitbox.Hitbox
import xyz.xenondevs.nova.world.point.Point3D
import java.util.*

private val SUPPORTED_NETWORK_TYPES = NetworkType.values().toHashSet()
private val ATTACHMENTS: IntArray = (64..112).toIntArray()

private val NetworkNode.itemHolder: ItemHolder?
    get() = if (this is NetworkEndPoint) (holders[ITEMS] as ItemHolder?) else null

private val NetworkNode.fluidHolder: FluidHolder?
    get() = if (this is NetworkEndPoint) holders[FLUID] as FluidHolder? else null

open class Cable(
    override val typeId: Int,
    override val energyTransferRate: Long,
    override val itemTransferRate: Int,
    override val fluidTransferRate: Long,
    uuid: UUID,
    data: CompoundElement,
    material: NovaMaterial,
    ownerUUID: UUID,
    armorStand: FakeArmorStand,
) : TileEntity(uuid, data, material, ownerUUID, armorStand), EnergyBridge, ItemBridge, FluidBridge {
    
    override val supportedNetworkTypes = SUPPORTED_NETWORK_TYPES
    override val networks = EnumMap<NetworkType, Network>(NetworkType::class.java)
    override val bridgeFaces = retrieveEnumCollectionOrNull("bridgeFaces", HashSet()) ?: CUBE_FACES.toMutableSet()
    
    override val gui: Lazy<TileEntityGUI>? = null
    
    override val connectedNodes: MutableMap<NetworkType, MutableMap<BlockFace, NetworkNode>> =
        NetworkType.values().associateWithTo(emptyEnumMap()) { enumMapOf() }
    
    private val configGUIs = emptyEnumMap<BlockFace, CableConfigGUI>()
    
    private val multiModel = createMultiModel()
    private val hitboxes = ArrayList<Hitbox>()
    
    override fun saveData() {
        super.saveData()
        storeList("bridgeFaces", bridgeFaces)
    }
    
    override fun handleNetworkUpdate() {
        // assume that we have NetworkManager.LOCK
        if (isValid && NOVA.isEnabled) {
            multiModel.replaceModels(getModelsNeeded())
            updateHeadStack()
            
            configGUIs.forEach { (face, gui) ->
                val neighbor = connectedNodes[ITEMS]?.get(face)
                
                fun closeAndRemove() {
                    runTask { gui.closeForAllViewers() }
                    configGUIs.remove(face)
                }
                
                if (neighbor is NetworkEndPoint) {
                    val itemHolder = neighbor.holders[ITEMS] as ItemHolder
                    if (itemHolder == gui.itemHolder) {
                        gui.updateValues(true)
                    } else closeAndRemove()
                } else closeAndRemove()
            }
            
            // !! Needs to be run in the server thread (updating blocks)
            // !! Also needs to be synchronized with NetworkManager as connectedNodes are retrieved
            NetworkManager.runSync { updateHitbox() }
        }
    }
    
    override fun handleInitialized(first: Boolean) {
        NetworkManager.runAsync { it.handleBridgeAdd(this) }
    }
    
    override fun handleHitboxPlaced() {
        super.handleHitboxPlaced()
        updateBlockHitbox()
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        
        val task: NetworkManagerTask = { it.handleBridgeRemove(this, unload) }
        if (NOVA.isEnabled) NetworkManager.runAsync(task) else NetworkManager.runNow(task)
        
        hitboxes.forEach { it.remove() }
        
        if (!unload) configGUIs.values.forEach(CableConfigGUI::closeForAllViewers)
    }
    
    override fun getHeadStack(): ItemStack {
        val connectedFaces = connectedNodes.values.flatMapTo(HashSet()) { it.keys }
        
        val booleans = CUBE_FACES.map { connectedFaces.contains(it) }.reversed().toBooleanArray()
        val number = MathUtils.convertBooleanArrayToInt(booleans)
        return material.block!!.createItemStack(number)
    }
    
    private fun getModelsNeeded(): List<Model> {
        require(networks.isNotEmpty()) { "No network is initialized" }
        
        val models = ArrayList<Model>()
        
        CUBE_FACES.forEach { face ->
            val oppositeFace = face.oppositeFace
            val itemHolder = connectedNodes[ITEMS]?.get(face)?.itemHolder
            val fluidHolder = connectedNodes[FLUID]?.get(face)?.fluidHolder
            
            if (itemHolder == null && fluidHolder == null) return@forEach
            
            val array = booleanArrayOf(
                itemHolder?.isExtract(oppositeFace) ?: false,
                itemHolder?.isInsert(oppositeFace) ?: false,
                fluidHolder?.isExtract(oppositeFace) ?: false,
                fluidHolder?.isInsert(oppositeFace) ?: false
            )
            
            val id = MathUtils.convertBooleanArrayToInt(array) +
                when (face) {
                    BlockFace.UP -> 16
                    BlockFace.DOWN -> 32
                    else -> 0
                }
            
            val attachmentStack = material.block!!.createItemStack(ATTACHMENTS[id])
            models += Model(
                attachmentStack,
                location.clone().center().apply { yaw = face.rotationValues.second * 90f }
            )
        }
        
        return models
    }
    
    private fun updateHitbox() {
        if (!isValid) return
        
        updateVirtualHitboxes()
        updateBlockHitbox()
    }
    
    private fun updateVirtualHitboxes() {
        hitboxes.forEach { it.remove() }
        hitboxes.clear()
        
        createCableHitboxes()
        createAttachmentHitboxes()
    }
    
    private fun createCableHitboxes() {
        CUBE_FACES.forEach { blockFace ->
            val pointA = Point3D(0.3, 0.3, 0.0)
            val pointB = Point3D(0.7, 0.7, 0.5)
            
            val origin = Point3D(0.5, 0.5, 0.5)
            
            val rotationValues = blockFace.rotationValues
            pointA.rotateAroundXAxis(rotationValues.first, origin)
            pointA.rotateAroundYAxis(rotationValues.second, origin)
            pointB.rotateAroundXAxis(rotationValues.first, origin)
            pointB.rotateAroundYAxis(rotationValues.second, origin)
            
            val sortedPoints = Point3D.sort(pointA, pointB)
            val from = location.clone().add(sortedPoints.first.x, sortedPoints.first.y, sortedPoints.first.z)
            val to = location.clone().add(sortedPoints.second.x, sortedPoints.second.y, sortedPoints.second.z)
            
            hitboxes += Hitbox(
                from, to,
                { it.action.isRightClick() && it.handItems.any { item -> item.novaMaterial == NovaMaterialRegistry.WRENCH } },
                { handleCableWrenchHit(it, blockFace) }
            )
        }
    }
    
    private fun createAttachmentHitboxes() {
        val neighbors = CUBE_FACES.associateWithNotNull { face ->
            val itemHolder = connectedNodes[ITEMS]?.get(face)?.itemHolder
            val fluidHolder = connectedNodes[FLUID]?.get(face)?.fluidHolder
            
            if (itemHolder != null || fluidHolder != null) {
                itemHolder to fluidHolder
            } else null
        }
        
        neighbors
            .forEach { (face, endPointDataHolders) ->
                val (itemHolder, fluidHolder) = endPointDataHolders
                
                val pointA = Point3D(0.125, 0.125, 0.0)
                val pointB = Point3D(0.875, 0.875, 0.2)
                
                val origin = Point3D(0.5, 0.5, 0.5)
                
                val rotationValues = face.rotationValues
                pointA.rotateAroundXAxis(rotationValues.first, origin)
                pointA.rotateAroundYAxis(rotationValues.second, origin)
                pointB.rotateAroundXAxis(rotationValues.first, origin)
                pointB.rotateAroundYAxis(rotationValues.second, origin)
                
                val sortedPoints = Point3D.sort(pointA, pointB)
                val from = location.clone().add(sortedPoints.first.x, sortedPoints.first.y, sortedPoints.first.z)
                val to = location.clone().add(sortedPoints.second.x, sortedPoints.second.y, sortedPoints.second.z)
                
                hitboxes += Hitbox(
                    from, to,
                    { it.action.isRightClick() && ProtectionManager.canUse(it.player, location) },
                    { handleAttachmentHit(it, itemHolder, fluidHolder, face) }
                )
            }
    }
    
    private fun updateBlockHitbox() {
        val block = location.block
        
        val neighborFaces = connectedNodes.flatMapTo(HashSet()) { it.value.keys }
        val axis = when {
            neighborFaces.contains(BlockFace.EAST) && neighborFaces.contains(BlockFace.WEST) -> Axis.X
            neighborFaces.contains(BlockFace.NORTH) && neighborFaces.contains(BlockFace.SOUTH) -> Axis.Z
            neighborFaces.contains(BlockFace.UP) && neighborFaces.contains(BlockFace.DOWN) -> Axis.Y
            else -> null
        }
        
        if (axis != null) {
            block.setType(Material.CHAIN, false)
            val blockData = block.blockData as Orientable
            blockData.axis = axis
            block.setBlockData(blockData, false)
        } else {
            block.setType(Material.STRUCTURE_VOID, false)
        }
    }
    
    private fun handleAttachmentHit(event: PlayerInteractEvent, itemHolder: ItemHolder?, fluidHolder: FluidHolder?, face: BlockFace) {
        if (!event.player.hasInventoryOpen) {
            event.isCancelled = true
            configGUIs.getOrPut(face) { CableConfigGUI(itemHolder, fluidHolder, face.oppositeFace) }.openWindow(event.player)
        }
    }
    
    private fun handleCableWrenchHit(event: PlayerInteractEvent, face: BlockFace) {
        event.isCancelled = true
        
        val player = event.player
        if (player.isSneaking) {
            Bukkit.getPluginManager().callEvent(BlockBreakEvent(location.block, player))
        } else if (ProtectionManager.canUse(player, location)) {
            NetworkManager.runAsync {
                if (connectedNodes.values.any { node -> node.containsKey(face) }) {
                    it.handleBridgeRemove(this, false)
                    bridgeFaces.remove(face)
                    it.handleBridgeAdd(this)
                } else if (!bridgeFaces.contains(face)) {
                    it.handleBridgeRemove(this, false)
                    bridgeFaces.add(face)
                    it.handleBridgeAdd(this)
                }
            }
        }
    }
    
    override fun handleTick() = Unit
    
}

private val BASIC_ENERGY_RATE = NovaConfig[NovaMaterialRegistry.BASIC_CABLE].getLong("energy_transfer_rate")!!
private val BASIC_ITEM_RATE = NovaConfig[NovaMaterialRegistry.BASIC_CABLE].getInt("item_transfer_rate")!!
private val BASIC_FLUID_RATE = NovaConfig[NovaMaterialRegistry.BASIC_CABLE].getLong("fluid_transfer_rate")!!

private val ADVANCED_ENERGY_RATE = NovaConfig[NovaMaterialRegistry.ADVANCED_CABLE].getLong("energy_transfer_rate")!!
private val ADVANCED_ITEM_RATE = NovaConfig[NovaMaterialRegistry.ADVANCED_CABLE].getInt("item_transfer_rate")!!
private val ADVANCED_FLUID_RATE = NovaConfig[NovaMaterialRegistry.ADVANCED_CABLE].getLong("fluid_transfer_rate")!!

private val ELITE_ENERGY_RATE = NovaConfig[NovaMaterialRegistry.ELITE_CABLE].getLong("energy_transfer_rate")!!
private val ELITE_ITEM_RATE = NovaConfig[NovaMaterialRegistry.ELITE_CABLE].getInt("item_transfer_rate")!!
private val ELITE_FLUID_RATE = NovaConfig[NovaMaterialRegistry.ELITE_CABLE].getLong("fluid_transfer_rate")!!

private val ULTIMATE_ENERGY_RATE = NovaConfig[NovaMaterialRegistry.ULTIMATE_CABLE].getLong("energy_transfer_rate")!!
private val ULTIMATE_ITEM_RATE = NovaConfig[NovaMaterialRegistry.ULTIMATE_CABLE].getInt("item_transfer_rate")!!
private val ULTIMATE_FLUID_RATE = NovaConfig[NovaMaterialRegistry.ULTIMATE_CABLE].getLong("fluid_transfer_rate")!!

class BasicCable(
    uuid: UUID,
    data: CompoundElement,
    material: NovaMaterial,
    ownerUUID: UUID,
    armorStand: FakeArmorStand,
) : Cable(
    0,
    BASIC_ENERGY_RATE,
    BASIC_ITEM_RATE,
    BASIC_FLUID_RATE,
    uuid,
    data,
    material,
    ownerUUID,
    armorStand,
)

class AdvancedCable(
    uuid: UUID,
    data: CompoundElement,
    material: NovaMaterial,
    ownerUUID: UUID,
    armorStand: FakeArmorStand,
) : Cable(
    1,
    ADVANCED_ENERGY_RATE,
    ADVANCED_ITEM_RATE,
    ADVANCED_FLUID_RATE,
    uuid,
    data,
    material,
    ownerUUID,
    armorStand,
)

class EliteCable(
    uuid: UUID,
    data: CompoundElement,
    material: NovaMaterial,
    ownerUUID: UUID,
    armorStand: FakeArmorStand,
) : Cable(
    2,
    ELITE_ENERGY_RATE,
    ELITE_ITEM_RATE,
    ELITE_FLUID_RATE,
    uuid,
    data,
    material,
    ownerUUID,
    armorStand,
)

class UltimateCable(
    uuid: UUID,
    data: CompoundElement,
    material: NovaMaterial,
    ownerUUID: UUID,
    armorStand: FakeArmorStand,
) : Cable(
    3,
    ULTIMATE_ENERGY_RATE,
    ULTIMATE_ITEM_RATE,
    ULTIMATE_FLUID_RATE,
    uuid,
    data,
    material,
    ownerUUID,
    armorStand,
)

class CreativeCable(
    uuid: UUID,
    data: CompoundElement,
    material: NovaMaterial,
    ownerUUID: UUID,
    armorStand: FakeArmorStand,
) : Cable(
    4,
    Long.MAX_VALUE,
    Int.MAX_VALUE,
    Long.MAX_VALUE,
    uuid,
    data,
    material,
    ownerUUID,
    armorStand,
)
