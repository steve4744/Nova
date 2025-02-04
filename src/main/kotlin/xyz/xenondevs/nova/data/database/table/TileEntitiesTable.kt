package xyz.xenondevs.nova.data.database.table

import org.jetbrains.exposed.dao.id.IdTable
import xyz.xenondevs.nova.data.database.columtype.compound
import xyz.xenondevs.nova.data.database.columtype.novaMaterial
import java.util.*

object TileEntitiesTable : IdTable<UUID>() {
    
    override val id = uuid("id").entityId()
    val owner = uuid("owner")
    val world = uuid("world")
    val chunkX = integer("chunkX")
    val chunkZ = integer("chunkZ")
    val x = integer("x")
    val y = integer("y")
    val z = integer("z")
    val yaw = float("yaw")
    val type = novaMaterial("type")
    val data = compound("data")
    
    override val primaryKey = PrimaryKey(id)
    
}
