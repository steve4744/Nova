package xyz.xenondevs.nova.integration.protection

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import xyz.xenondevs.nova.integration.Integration
import xyz.xenondevs.nova.integration.protection.plugin.*
import xyz.xenondevs.nova.util.isBetweenXZ
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.pos
import java.util.*
import java.util.concurrent.TimeUnit

object ProtectionManager {
    
    private val PROTECTION_PLUGINS = listOf(GriefPrevention, PlotSquared, WorldGuard, GriefDefender, Towny)
        .filter(Integration::isInstalled)
    
    private val PROTECTION_CACHE: Cache<ProtectionLookupKey, Boolean> =
        CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build()
    
    fun canPlace(uuid: UUID, location: Location) =
        canPlace(Bukkit.getOfflinePlayer(uuid), location)
    
    fun canBreak(uuid: UUID, location: Location) =
        canBreak(Bukkit.getOfflinePlayer(uuid), location)
    
    fun canUse(uuid: UUID, location: Location) =
        canUse(Bukkit.getOfflinePlayer(uuid), location)
    
    fun canPlace(offlinePlayer: OfflinePlayer, location: Location): Boolean {
        return PROTECTION_CACHE.get(ProtectionLookupKey(0, offlinePlayer.uniqueId, location.pos)) {
            !isVanillaProtected(offlinePlayer, location)
                && PROTECTION_PLUGINS.all { it.canPlace(offlinePlayer, location) }
        }
    }
    
    fun canBreak(offlinePlayer: OfflinePlayer, location: Location): Boolean {
        return PROTECTION_CACHE.get(ProtectionLookupKey(1, offlinePlayer.uniqueId, location.pos)) {
            !isVanillaProtected(offlinePlayer, location)
                && PROTECTION_PLUGINS.all { it.canBreak(offlinePlayer, location) }
        }
    }
    
    fun canUse(offlinePlayer: OfflinePlayer, location: Location): Boolean {
        return PROTECTION_CACHE.get(ProtectionLookupKey(2, offlinePlayer.uniqueId, location.pos)) {
            !isVanillaProtected(offlinePlayer, location)
                && PROTECTION_PLUGINS.all { it.canUse(offlinePlayer, location) }
        }
    }
    
    private fun isVanillaProtected(offlinePlayer: OfflinePlayer, location: Location): Boolean {
        val spawnRadius = Bukkit.getServer().spawnRadius.toDouble()
        val world = location.world!!
        return world.name == "world"
            && spawnRadius > 0
            && !offlinePlayer.isOp
            && location.isBetweenXZ(
            world.spawnLocation.subtract(spawnRadius, 0.0, spawnRadius),
            world.spawnLocation.add(spawnRadius, 0.0, spawnRadius)
        )
    }
    
}

private data class ProtectionLookupKey(val type: Int, val uuid: UUID, val pos: BlockPos)