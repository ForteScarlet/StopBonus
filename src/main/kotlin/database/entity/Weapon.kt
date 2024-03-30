package database.entity

import org.jetbrains.exposed.dao.id.EntityID

/**
 * 武器信息。
 */
object Weapons : BaseIntIdTable() {
    val account = referenceAccount()
    val name = varchar("name", 1000).index()
}

/**
 * 武器
 */
class Weapon(id: EntityID<Int>) : BaseIntEntity(id, Weapons) {
    companion object : BaseEntityClass<Weapon>(Weapons)

    var account by Account referencedOn Weapons.account
    var name by Weapons.name
}


data class WeaponView(
    override val entityID: EntityID<Int>,
    val name: String
) : BaseIntEntityView

fun Weapon.toView(
    name: String = this.name,
): WeaponView = WeaponView(
    entityID = id,
    name = name
)
