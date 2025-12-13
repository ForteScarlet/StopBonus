package common

import androidx.compose.ui.unit.dp

/**
 * 应用中使用的 Emoji 常量
 */
object Emojis {
    const val ANGRY = "\uD83D\uDE21"   // 😡
    const val CLOCK = "\u23F1"         // ⏱
    const val CRY = "\uD83D\uDE22"     // 😢
    const val SWEAT = "\uD83D\uDE10"   // 😰
}

/**
 * 数据限制常量
 */
object Limits {
    /** 备注字段最大长度 */
    const val REMARK_MAX_LENGTH = 500
}

/**
 * UI 尺寸常量
 */
object Dimensions {
    val TopBarHorizontalPadding = 70.dp
    val TopBarVerticalPadding = 50.dp
    val StandardSpacing = 15.dp

    // 选择器相关尺寸
    val SelectorMinWidth = 120.dp
    val SelectorMaxWidth = 160.dp
    val YearInputWidth = 120.dp
    val FlowRowVerticalSpacing = 12.dp
}
