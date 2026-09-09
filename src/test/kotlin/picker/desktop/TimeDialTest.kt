package picker.desktop

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import picker.core.HourCycle

/**
 * 覆盖圆形表盘的坐标到时间数值映射。
 */
class TimeDialTest {
    @Test
    fun 二十四小时制按点击半径区分内外圈小时() {
        assertEquals(0, hourFromPoint(Offset(108f, 20f), 216f, 216f, HourCycle.H24))
        assertEquals(12, hourFromPoint(Offset(108f, 70f), 216f, 216f, HourCycle.H24))
        assertEquals(15, hourFromPoint(Offset(196f, 108f), 216f, 216f, HourCycle.H24))
    }

    @Test
    fun 分钟表盘按最近五分钟刻度取值() {
        assertEquals(0, minuteFromPoint(Offset(108f, 16f), 216f, 216f))
        assertEquals(15, minuteFromPoint(Offset(200f, 108f), 216f, 216f))
        assertEquals(30, minuteFromPoint(Offset(108f, 200f), 216f, 216f))
    }

    @Test
    fun 跨越零点时沿最近方向旋转() {
        assertEquals(12f, nearestCircularIndex(current = 11f, target = 0f, divisionCount = 12))
        assertEquals(-1f, nearestCircularIndex(current = 0f, target = 11f, divisionCount = 12))
        assertEquals(60f, nearestCircularIndex(current = 59f, target = 0f, divisionCount = 60))
        assertEquals(-1f, nearestCircularIndex(current = 0f, target = 59f, divisionCount = 60))
    }
}
