package xyz.xenondevs.nova.player.advancement.mob

import net.roxeez.advancement.Advancement
import org.bukkit.NamespacedKey
import xyz.xenondevs.nova.NOVA
import xyz.xenondevs.nova.material.NovaMaterialRegistry.MOB_CATCHER
import xyz.xenondevs.nova.player.advancement.RootAdvancement
import xyz.xenondevs.nova.player.advancement.addObtainCriteria
import xyz.xenondevs.nova.player.advancement.setDisplayLocalized
import xyz.xenondevs.nova.player.advancement.toIcon

object MobCatcherAdvancement : Advancement(NamespacedKey(NOVA, "mob_catcher")) {
    
    init {
        setParent(RootAdvancement.key)
        addObtainCriteria(MOB_CATCHER)
        setDisplayLocalized {
            it.setIcon(MOB_CATCHER.toIcon())
        }
    }
    
}