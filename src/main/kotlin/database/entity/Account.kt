package database.entity

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/**
 * 账户，用于区分数据。
 */
object Accounts : BaseIntIdTable() {
    val name = varchar("name", 50)
}

class Account(id: EntityID<Int>) : BaseIntEntity(id, Accounts) {
    companion object : BaseEntityClass<Account>(Accounts)

    var name by Accounts.name

    val records by BonusRecord referrersOn BonusRecords.account
    val weapons by Weapon referrersOn Weapons.account
}

fun Table.referenceAccount(
    columnName: String = "account_id",
    onDelete: ReferenceOption = ReferenceOption.CASCADE,
    onUpdate: ReferenceOption = ReferenceOption.CASCADE,
    fkName: String? = null
): Column<EntityID<Int>> =
    reference(columnName, Accounts, onDelete, onUpdate, fkName)


data class AccountView(
    override val entityID: EntityID<Int>,
    val name: String,
) : BaseIntEntityView

fun Account.toView(
    name: String = this.name,
): AccountView = AccountView(
    id,
    name = name,
)
