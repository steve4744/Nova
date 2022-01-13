package xyz.xenondevs.nova.world.loot

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.LootGenerateEvent
import xyz.xenondevs.nova.NOVA
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.util.data.GSON
import xyz.xenondevs.nova.util.data.fromJson
import kotlin.random.Random
import kotlin.random.nextInt

private val LOOT_CONFIG = NovaConfig["loot"]

object LootGeneration : Listener {
    
    private val lootTable = ArrayList<LootInfo>()
    
    fun init() {
        lootTable.addAll(GSON.fromJson<ArrayList<LootInfo>>(LOOT_CONFIG.getArray("loot")) ?: emptyList())
        Bukkit.getServer().pluginManager.registerEvents(this, NOVA)
    }
    
    @EventHandler
    fun handleLootGenerationEvent(event: LootGenerateEvent) {
        lootTable.forEach { loot ->
            if (loot.isWhitelisted(event.lootTable.key) && Random.nextInt(1, 100) <= loot.frequency) {
                val amount = Random.nextInt(loot.min..loot.max)
                event.loot.add(loot.item.createItemStack(amount))
            }
        }
    }
    
}