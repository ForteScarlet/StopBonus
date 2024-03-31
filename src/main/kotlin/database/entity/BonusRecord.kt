package database.entity

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.duration
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Duration
import java.time.Instant

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

    /**
     * 备注
     */
    val remark = varchar("remark", 500).default("")
}

class BonusRecord(id: EntityID<Int>) : BaseIntEntity(id, BonusRecords) {
    companion object : BaseEntityClass<BonusRecord>(BonusRecords)

    var account by Account referencedOn BonusRecords.account
    var startTime by BonusRecords.startTime
    var endTime by BonusRecords.endTime
    var duration by BonusRecords.duration
    var score by BonusRecords.score
    var remark by BonusRecords.remark

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

data class BonusRecordView(
    override val entityID: EntityID<Int>,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration,
    val score: UInt,
    val remark: String,
    val weapons: List<WeaponView>
) : BaseIntEntityView

fun BonusRecord.toView(
    startTime: Instant = this.startTime,
    endTime: Instant = this.endTime,
    duration: Duration = this.duration,
    score: UInt = this.score,
    remark: String = this.remark,
    weapons: List<WeaponView> = this.weapons.map { it.toView() }
): BonusRecordView = BonusRecordView(
    entityID = id,
    startTime = startTime,
    endTime = endTime,
    duration = duration,
    score = score,
    remark = remark,
    weapons = weapons,
)
