package picker.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransition

/**
 * 本地日期时间的有效偏移量及其解析得到的时刻。
 */
data class OffsetCandidate(
    /**
     * 时区规则提供的偏移量。
     */
    val offset: ZoneOffset,
    /**
     * 将 [offset] 应用于本地值后得到的绝对时刻。
     */
    val instant: Instant,
)

/**
 * 按时区转换规则解析本地日期时间的结果。
 */
sealed interface InstantResolution {
    /**
     * 本地日期时间仅适用一个偏移量。
     */
    data class Unique(
        /**
         * 唯一有效的偏移量及对应时刻。
         */
        val candidate: OffsetCandidate,
    ) : InstantResolution

    /**
     * 本地日期时间落在夏令时缺口内，实际并不存在。
     */
    data class Gap(
        /**
         * 描述缺失本地时间区间的转换规则。
         */
        val transition: ZoneOffsetTransition,
    ) : InstantResolution

    /**
     * 存在两个适用偏移量，调用方必须明确选择。
     */
    data class Overlap(
        /**
         * 按实际时刻而非偏移量文本排序的候选项。
         */
        val candidates: List<OffsetCandidate>,
    ) : InstantResolution
}

/**
 * 解析本地日期时间，不应用库默认的夏令时回退策略。
 */
fun resolveInstant(
    /**
     * 用户输入的本地墙上时间。
     */
    local: LocalDateTime,
    /**
     * 提供历史和未来转换规则的时区。
     */
    zone: ZoneId,
): InstantResolution {
    val rules = zone.rules
    val candidates = rules.getValidOffsets(local)
        .map { offset -> OffsetCandidate(offset, local.toInstant(offset)) }
        .sortedBy(OffsetCandidate::instant)

    // ZoneRules 会明确暴露缺口与重叠，界面也应保留该差异而不猜测用户意图。
    return when (candidates.size) {
        0 -> InstantResolution.Gap(
            transition = checkNotNull(rules.getTransition(local)) {
                "ZoneRules returned no offset and no transition for $local in $zone"
            },
        )

        1 -> InstantResolution.Unique(candidates.single())
        else -> InstantResolution.Overlap(candidates)
    }
}

/**
 * 仅当 [selectedOffset] 当前有效时解析本地值。
 */
fun resolveSelectedInstant(
    /**
     * 待解析的本地墙上时间。
     */
    local: LocalDateTime,
    /**
     * 用于检查有效偏移量的时区。
     */
    zone: ZoneId,
    /**
     * 用户选择的重叠偏移量；尚未选择时为 `null`。
     */
    selectedOffset: ZoneOffset?,
): Instant? {
    val validOffsets = zone.rules.getValidOffsets(local)
    if (selectedOffset == null || selectedOffset !in validOffsets) return null
    return local.toInstant(selectedOffset)
}
