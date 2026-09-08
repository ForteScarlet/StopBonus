package database.entity

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.DaoEntityID


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
