package database.entity

import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.id.EntityID


/**
 *
 * @author ForteScarlet
 */
interface BaseEntityView<ID : Comparable<ID>> {
    val entityID: EntityID<ID>
    val id: ID
        get() = entityID.value

}

/**
 *
 * @author ForteScarlet
 */
interface BaseIntEntityView : BaseEntityView<Int>
