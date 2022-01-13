package xyz.xenondevs.nova.ui.config.side

import de.studiocode.invui.item.builder.ItemBuilder
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.TranslatableComponent
import org.bukkit.block.BlockFace
import xyz.xenondevs.nova.tileentity.TileEntity
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.NetworkManager
import xyz.xenondevs.nova.tileentity.network.item.holder.ItemHolder
import xyz.xenondevs.nova.tileentity.network.item.inventory.NetworkedInventory
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.data.addLoreLines

class ItemSideConfigGUI(
    val itemHolder: ItemHolder,
    inventories: List<Pair<NetworkedInventory, String>>
) : InventorySideConfigGUI() {
    
    private val inventories = inventories.map { it.first }
    private val allowedTypes = itemHolder.allowedConnectionTypes.mapValues { (_, type) -> type.included }
    private val buttonBuilders = inventories.withIndex().associate { (index, triple) ->
        triple.first to BUTTON_COLORS[index]
            .createBasicItemBuilder()
            .addLoreLines(TranslatableComponent(triple.second).apply { color = ChatColor.AQUA })
    }
    
    init {
        initGUI()
    }
    
    override fun changeConnectionType(blockFace: BlockFace, forward: Boolean): Boolean {
        NetworkManager.runNow { // TODO: runSync / runAsync ?
            val allowedTypes = allowedTypes[itemHolder.inventories[blockFace]!!]!!
            
            it.handleEndPointRemove(itemHolder.endPoint, true)
            
            val currentType = itemHolder.itemConfig[blockFace]!!
            var index = allowedTypes.indexOf(currentType)
            if (forward) index++ else index--
            if (index < 0) index = allowedTypes.lastIndex
            else if (index == allowedTypes.size) index = 0
            itemHolder.itemConfig[blockFace] = allowedTypes[index]
            
            it.handleEndPointAdd(itemHolder.endPoint, false)
            itemHolder.endPoint.updateNearbyBridges()
        }
        
        return true
    }
    
    override fun changeInventory(blockFace: BlockFace, forward: Boolean): Boolean {
        if (inventories.size < 2) return false
        
        NetworkManager.runNow { // TODO: runSync / runAsync ?
            it.handleEndPointRemove(itemHolder.endPoint, false)
            
            val currentInventory = itemHolder.inventories[blockFace]!!
            var index = inventories.indexOf(currentInventory)
            if (forward) index++ else index--
            if (index < 0) index = inventories.lastIndex
            else if (index == inventories.size) index = 0
            
            val newInventory = inventories[index]
            itemHolder.inventories[blockFace] = newInventory
            
            val allowedTypes = allowedTypes[newInventory]!!
            if (!allowedTypes.contains(itemHolder.itemConfig[blockFace]!!)) {
                itemHolder.itemConfig[blockFace] = allowedTypes[0]
            }
            
            it.handleEndPointAdd(itemHolder.endPoint)
        }
        
        return true
    }
    
    override fun getBlockFace(blockSide: BlockSide): BlockFace {
        return (itemHolder.endPoint as TileEntity).getFace(blockSide)
    }
    
    override fun getConnectionType(blockFace: BlockFace): NetworkConnectionType {
        // TODO: surround with NetworkManager lock
        return itemHolder.itemConfig[blockFace]!!
    }
    
    override fun getInventoryButtonBuilder(blockFace: BlockFace): ItemBuilder {
        // TODO: surround with NetworkManager lock
        return buttonBuilders[itemHolder.inventories[blockFace]!!]!!
    }
    
}