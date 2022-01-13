package xyz.xenondevs.nova.player.advancement.stardust

import net.roxeez.advancement.Advancement
import org.bukkit.NamespacedKey
import xyz.xenondevs.nova.NOVA
import xyz.xenondevs.nova.material.NovaMaterialRegistry
import xyz.xenondevs.nova.player.advancement.RootAdvancement
import xyz.xenondevs.nova.player.advancement.addObtainCriteria
import xyz.xenondevs.nova.player.advancement.setDisplayLocalized
import xyz.xenondevs.nova.player.advancement.toIcon

object StarShardsAdvancement : Advancement(NamespacedKey(NOVA, "star_shards")) {
    
    init {
        setParent(RootAdvancement.key)
        addObtainCriteria(NovaMaterialRegistry.STAR_SHARDS)
        setDisplayLocalized { 
            it.setIcon(NovaMaterialRegistry.STAR_SHARDS.toIcon())
        }
    }
    
}