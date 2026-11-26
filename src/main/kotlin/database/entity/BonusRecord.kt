package database.entity

import java.time.Duration
import java.time.Instant

data class BonusRecordView(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration,
    val score: UInt,
    val remark: String,
    val weapons: List<WeaponView>,
)
