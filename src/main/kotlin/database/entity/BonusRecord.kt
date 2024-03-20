package database.entity

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.duration
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * 奖励记录
 */
object BonusRecords : BaseIntIdTable() {
    val account = referenceAccount()

    /**
     * 开始时间
     */
    val startTime = timestamp("start_time")

    /**
     * 结束时间
     */
    val endTime = timestamp("end_time")

    /**
     * 持续时间
     */
    val duration = duration("duration")

    /**
     * 给本次打分。0~10分。
     */
    val score = uinteger("score").default(0u)
}

class BonusRecord(id: EntityID<Int>) : BaseIntEntity(id, BonusRecords) {
    companion object : BaseEntityClass<BonusRecord>(BonusRecords)

    var account by Account referencedOn BonusRecords.account
    var startTime by BonusRecords.startTime
    var endTime by BonusRecords.endTime
    var duration by BonusRecords.duration
    var score by BonusRecords.score

    var weapons by Weapon via BonusRecordWeapons
}

/**
 * 中间表性质，记录一次提交记录中使用的所有武器道具。
 *
 * @see Weapons
 */
object BonusRecordWeapons : Table() {
    val record = reference(
        "record_id", BonusRecords,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE,
    )
    val weapon = reference(
        "weapon_id", Weapons,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE,
    )
    override val primaryKey: PrimaryKey = PrimaryKey(record, weapon)
}

