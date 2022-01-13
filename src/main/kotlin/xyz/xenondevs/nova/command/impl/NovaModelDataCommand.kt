package xyz.xenondevs.nova.command.impl

import com.mojang.brigadier.context.CommandContext
import net.md_5.bungee.api.ChatColor
import net.minecraft.commands.CommandSourceStack
import xyz.xenondevs.nova.command.*
import xyz.xenondevs.nova.material.NovaMaterial
import xyz.xenondevs.nova.material.NovaMaterialRegistry
import xyz.xenondevs.nova.util.data.coloredText
import xyz.xenondevs.nova.util.data.localized
import xyz.xenondevs.nova.util.novaMaterial

object NovaModelDataCommand : Command("nvmodeldata") {
    
    init {
        builder = builder
            .requiresPermission("nova.command.modeldata")
            .apply {
                NovaMaterialRegistry.sortedValues.forEach { material ->
                    then(literal(material.typeName.lowercase())
                        .executesCatching { showModelData(material, it) }
                    )
                }
            }
            .executesCatching { showCurrentModelData(it) }
    }
    
    private fun showCurrentModelData(ctx: CommandContext<CommandSourceStack>) {
        val player = ctx.player
        val material = player.inventory.itemInMainHand.novaMaterial
        if (material != null) {
            showModelData(material, ctx)
        } else ctx.source.sendFailure(localized(ChatColor.RED, "command.nova.modeldata.no-nova-item"))
    }
    
    private fun showModelData(material: NovaMaterial, ctx: CommandContext<CommandSourceStack>) {
        val localizedName: Any =
            if (material.localizedName.isNotEmpty()) localized(ChatColor.AQUA, material.localizedName)
            else coloredText(ChatColor.AQUA, material.typeName)
        
        // Send item info
        val item = material.item.material
        val itemLocalized = localized(ChatColor.AQUA, item)
        
        ctx.source.sendSuccess(localized(
            ChatColor.GRAY,
            "command.nova.modeldata.item",
            localizedName,
            itemLocalized,
            coloredText(ChatColor.AQUA, material.item.dataArray.contentToString()),
        ))
        
        if (material.block != null) {
            // Send block info
            val block = material.block.material
            val blockLocalized = localized(ChatColor.AQUA, block)
            
            ctx.source.sendSuccess(localized(
                ChatColor.GRAY,
                "command.nova.modeldata.block",
                localizedName,
                blockLocalized,
                coloredText(ChatColor.AQUA, material.block.dataArray.contentToString()),
            ))
        }
    }
    
}