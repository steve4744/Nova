package xyz.xenondevs.nova.tileentity.impl.world

import de.studiocode.invui.gui.GUI
import de.studiocode.invui.gui.builder.GUIBuilder
import de.studiocode.invui.gui.builder.guitype.GUIType
import org.bukkit.Material
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.serialization.cbf.element.CompoundElement
import xyz.xenondevs.nova.integration.other.ItemsAdder
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.material.NovaMaterial
import xyz.xenondevs.nova.material.NovaMaterialRegistry.BLOCK_PLACER
import xyz.xenondevs.nova.tileentity.*
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.energy.EnergyConnectionType
import xyz.xenondevs.nova.tileentity.network.energy.holder.ConsumerEnergyHolder
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.tileentity.upgrade.UpgradeHolder
import xyz.xenondevs.nova.tileentity.upgrade.UpgradeType
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigGUI
import xyz.xenondevs.nova.util.*
import xyz.xenondevs.nova.world.armorstand.FakeArmorStand
import java.util.*

private val MAX_ENERGY = NovaConfig[BLOCK_PLACER].getLong("capacity")!!
private val ENERGY_PER_PLACE = NovaConfig[BLOCK_PLACER].getLong("energy_per_place")!!

class BlockPlacer(
    uuid: UUID,
    data: CompoundElement,
    material: NovaMaterial,
    ownerUUID: UUID,
    armorStand: FakeArmorStand,
) : NetworkedTileEntity(uuid, data, material, ownerUUID, armorStand), Upgradable {
    
    private val inventory = getInventory("inventory", 9) {}
    override val gui = lazy { BlockPlacerGUI() }
    override val upgradeHolder = UpgradeHolder(this, gui, UpgradeType.EFFICIENCY, UpgradeType.ENERGY)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_PLACE, 0, upgradeHolder) { createEnergySideConfig(EnergyConnectionType.CONSUME, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.BUFFER)
    
    private val placeLocation = location.clone().advance(getFace(BlockSide.FRONT))
    private val placeBlock = location.clone().advance(getFace(BlockSide.FRONT)).block
    
    private fun placeBlock(): Boolean {
        for ((index, item) in inventory.items.withIndex()) {
            if (item == null) continue
            
            val material = item.type
            if (material.isBlock) {
                val novaMaterial = item.novaMaterial
                if (novaMaterial != null && novaMaterial.isBlock) {
                    if (TileEntityLimits.canPlaceTileEntity(ownerUUID, world, novaMaterial) == PlaceResult.ALLOW) {
                        TileEntityManager.placeTileEntity(ownerUUID, placeBlock.location, armorStand.location.yaw, novaMaterial, null)
                        novaMaterial.hitboxType?.playPlaceSoundEffect(placeBlock.location)
                    } else continue
                } else {
                    placeBlock.place(item)
                    material.playPlaceSoundEffect(placeBlock.location)
                }
                
                inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                return true
            } else if (ItemsAdder.isInstalled) {
                if (ItemsAdder.placeItem(item, placeBlock.location)) {
                    inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                    return true
                }
            }
        }
        
        return false
    }
    
    override fun handleTick() {
        val type = placeBlock.type
        if (energyHolder.energy >= energyHolder.energyConsumption
            && !inventory.isEmpty
            && type == Material.AIR
            && TileEntityManager.getTileEntityAt(placeLocation) == null
            && ProtectionManager.canPlace(ownerUUID, placeBlock.location)
        ) {
            if (placeBlock()) energyHolder.energy -= energyHolder.energyConsumption
        }
    }
    
    inner class BlockPlacerGUI : TileEntityGUI() {
        
        private val sideConfigGUI = SideConfigGUI(
            this@BlockPlacer,
            listOf(EnergyConnectionType.NONE, EnergyConnectionType.CONSUME),
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default")
        ) { openWindow(it) }
        
        override val gui: GUI = GUIBuilder(GUIType.NORMAL, 9, 5)
            .setStructure("" +
                "1 - - - - - - - 2" +
                "| s # i i i # . |" +
                "| u # i i i # . |" +
                "| # # i i i # . |" +
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGUI))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .build()
        
        val energyBar = EnergyBar(gui, x = 7, y = 1, height = 3, energyHolder)
        
    }
    
}