package database.entity

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

private fun currentInstant() = Instant.now()

abstract class BaseIntIdTable : IntIdTable {
    constructor(name: String) : super(name)
    constructor() : super()

    val createTime = timestamp("create_time").clientDefault { currentInstant() }
    val lastUpdatedTime = timestamp("last_updated_time").clientDefault { currentInstant() }
    // val version = integer("version").default(0)
}

abstract class BaseIntEntity(id: EntityID<Int>, table: BaseIntIdTable) : IntEntity(id) {
    val createTime by table.createTime
    var lastUpdatedTime by table.lastUpdatedTime
    // var version by table.version
}

abstract class BaseEntityClass<E : BaseIntEntity>(table: BaseIntIdTable) : IntEntityClass<E>(table) {
    init {
        EntityHook.subscribe { action ->
            if (action.changeType == EntityChangeType.Updated) {
                try {
                    action.toEntity(this)?.lastUpdatedTime = currentInstant()
                } catch (e: Exception) {
                    //nothing much to do here
                }
            }
        }
    }
}
