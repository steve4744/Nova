package xyz.xenondevs.nova.tileentity.impl

import de.studiocode.invui.gui.builder.GUIBuilder
import de.studiocode.invui.gui.builder.GUIType
import de.studiocode.invui.item.ItemBuilder
import de.studiocode.invui.window.impl.single.SimpleWindow
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.xenondevs.nova.material.NovaMaterial
import xyz.xenondevs.nova.network.energy.EnergyConnectionType.*
import xyz.xenondevs.nova.tileentity.EnergyTileEntity
import xyz.xenondevs.nova.tileentity.TileEntity
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.config.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.SideConfigGUI
import xyz.xenondevs.nova.util.EnergyUtils

open class PowerCell(
    val maxEnergy: Int,
    material: NovaMaterial,
    armorStand: ArmorStand,
) : EnergyTileEntity(material, armorStand) {
    
    override val defaultEnergyConfig by lazy { createEnergySideConfig(BUFFER) }
    override val requestedEnergy: Int
        get() = maxEnergy - energy
    
    private val gui = PowerCellUI()
    
    override fun handleRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        gui.openWindow(event.player)
    }
    
    override fun handleTick() {
        if (hasEnergyChanged) {
            gui.energyBar.update()
            hasEnergyChanged = false
        }
    }
    
    private inner class PowerCellUI {
        
        private val sideConfigGUI = SideConfigGUI(
            this@PowerCell,
            listOf(NONE, PROVIDE, CONSUME, BUFFER),
            null
        ) { openWindow(it) }
        
        private val gui = GUIBuilder(GUIType.NORMAL, 9, 5)
            .setStructure("" +
                "1 - - - - - - - 2" +
                "| s # # . # # # |" +
                "| # # # . # # # |" +
                "| # # # . # # # |" +
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGUI))
            .build()
        
        val energyBar = EnergyBar(gui, x = 4, y = 1, height = 3) { energy to maxEnergy }
        
        fun openWindow(player: Player) {
            SimpleWindow(player, "Power Cell", gui).show()
        }
        
    }
    
    companion object {
        
        fun createItemBuilder(material: NovaMaterial, tileEntity: TileEntity?): ItemBuilder {
            val builder = material.createBasicItemBuilder()
            val energy = tileEntity?.let { (tileEntity as PowerCell).energy } ?: 0
            val maxEnergy = tileEntity?.let { (tileEntity as PowerCell).maxEnergy } ?: 0
            builder.addLoreLines(EnergyUtils.getEnergyString(energy, maxEnergy))
            return builder
        }
        
    }
    
}

class BasicPowerCell(material: NovaMaterial, armorStand: ArmorStand) : PowerCell(100_000, material, armorStand)

class AdvancedPowerCell(material: NovaMaterial, armorStand: ArmorStand) : PowerCell(1_000_000, material, armorStand)

class ElitePowerCell(material: NovaMaterial, armorStand: ArmorStand) : PowerCell(5_000_000, material, armorStand)

class UltimatePowerCell(material: NovaMaterial, armorStand: ArmorStand) : PowerCell(20_000_000, material, armorStand)

class CreativePowerCell(material: NovaMaterial, armorStand: ArmorStand) : PowerCell(Int.MAX_VALUE, material, armorStand)
