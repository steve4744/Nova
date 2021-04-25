package xyz.xenondevs.nova.ui.config

import com.google.common.base.Preconditions
import de.studiocode.invui.gui.GUI
import de.studiocode.invui.gui.builder.GUIBuilder
import de.studiocode.invui.gui.builder.GUIType
import de.studiocode.invui.gui.impl.SimpleGUI
import de.studiocode.invui.item.impl.SimpleItem
import de.studiocode.invui.resourcepack.Icon
import de.studiocode.invui.virtualinventory.VirtualInventory
import de.studiocode.invui.window.impl.single.SimpleWindow
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.xenondevs.nova.material.NovaMaterial
import xyz.xenondevs.nova.network.NetworkEndPoint
import xyz.xenondevs.nova.network.energy.EnergyConnectionType
import xyz.xenondevs.nova.network.energy.EnergyStorage
import xyz.xenondevs.nova.network.item.ItemConnectionType
import xyz.xenondevs.nova.network.item.ItemStorage
import xyz.xenondevs.nova.ui.item.ClickyTabItem

private val ALLOWED_ITEM_TYPES = listOf(ItemConnectionType.NONE, ItemConnectionType.INSERT, ItemConnectionType.EXTRACT, ItemConnectionType.BUFFER)

class SideConfigGUI(
    endPoint: NetworkEndPoint,
    allowedEnergyTypes: List<EnergyConnectionType>?,
    inventoryNames: List<Pair<VirtualInventory, String>>?,
    openPrevious: (Player) -> Unit
) {
    
    private val energyConfigGUI = if (allowedEnergyTypes != null)
        EnergySideConfigGUI(endPoint as EnergyStorage, allowedEnergyTypes) else null
    
    private val itemConfigGUI = if (inventoryNames != null)
        ItemSideConfigGUI(endPoint as ItemStorage, ALLOWED_ITEM_TYPES, inventoryNames) else null
    
    private val mainGUI: GUI
    
    init {
        Preconditions.checkArgument(energyConfigGUI != null || itemConfigGUI != null)
        
        if (energyConfigGUI != null && itemConfigGUI != null) {
            mainGUI = GUIBuilder(GUIType.TAB, 9, 3)
                .setStructure("" +
                    "b x x x x x x x x" +
                    "e x x x x x x x x" +
                    "i x x x x x x x x")
                .addIngredient('b', BackItem(openPrevious))
                .addIngredient('e', ClickyTabItem(0) {
                    if (it.currentTab == 0) NovaMaterial.ENERGY_OFF_BUTTON.createBasicItemBuilder()
                    else NovaMaterial.ENERGY_ON_BUTTON.createBasicItemBuilder().setDisplayName("§rEnergy Side Config")
                })
                .addIngredient('i', ClickyTabItem(1) {
                    if (it.currentTab == 1) NovaMaterial.ITEM_OFF_BUTTON.createBasicItemBuilder()
                    else NovaMaterial.ITEM_ON_BUTTON.createBasicItemBuilder().setDisplayName("§rItem Side Config")
                })
                .addGUI(energyConfigGUI)
                .addGUI(itemConfigGUI)
                .build()
        } else {
            val chosenGUI = energyConfigGUI ?: itemConfigGUI!!
            mainGUI = SimpleGUI(9, 3)
            mainGUI.setItem(0, BackItem(openPrevious))
            mainGUI.fillRectangle(1, 0, chosenGUI, true)
            mainGUI.fill(SimpleItem(Icon.BACKGROUND.itemBuilder), false)
        }
    }
    
    fun openWindow(player: Player) {
        SimpleWindow(player, "Side Config", mainGUI).show()
    }
    
}

class OpenSideConfigItem(private val sideConfigGUI: SideConfigGUI) : SimpleItem(NovaMaterial.SIDE_CONFIG_BUTTON.item.getItemBuilder("Side Config")) {
    
    override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
        sideConfigGUI.openWindow(player)
    }
    
}

class BackItem(private val openPrevious: (Player) -> Unit) : SimpleItem(Icon.ARROW_1_LEFT.itemBuilder) {
    
    override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
        openPrevious(player)
    }
    
}