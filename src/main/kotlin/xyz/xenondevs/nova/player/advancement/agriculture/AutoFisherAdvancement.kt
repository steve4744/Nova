package xyz.xenondevs.nova.player.advancement.agriculture

import net.roxeez.advancement.Advancement
import org.bukkit.NamespacedKey
import xyz.xenondevs.nova.NOVA
import xyz.xenondevs.nova.material.NovaMaterialRegistry.AUTO_FISHER
import xyz.xenondevs.nova.player.advancement.RootAdvancement
import xyz.xenondevs.nova.player.advancement.addObtainCriteria
import xyz.xenondevs.nova.player.advancement.setDisplayLocalized
import xyz.xenondevs.nova.player.advancement.toIcon

object AutoFisherAdvancement : Advancement(NamespacedKey(NOVA, "auto_fisher")) {
    
    init {
        setParent(RootAdvancement.key)
        addObtainCriteria(AUTO_FISHER)
        setDisplayLocalized {
            it.setIcon(AUTO_FISHER.toIcon())
        }
    }
    
}