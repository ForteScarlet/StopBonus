package picker.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import picker.core.PickerEnvironment
import picker.foundation.PickerSemantics
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DateTimePickerInteractionTest {
    @Test
    fun escapeDiscardsDraftAndConfirmationReturnsFocusToInput() = runComposeUiTest {
        val original = LocalDateTime.of(2026, 11, 25, 9, 30)
        val selected = LocalDate.of(2026, 11, 26)
        val environment = PickerEnvironment(Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC))
        val changes = mutableListOf<LocalDateTime?>()
        setContent {
            var value by remember { mutableStateOf<LocalDateTime?>(original) }
            Box(Modifier.width(400.dp)) {
                DateTimePickerField(
                    value = value,
                    onValueChange = {
                        changes.add(it)
                        value = it
                    },
                    environment = environment,
                    slots = DateTimePickerDefaults.slots(actions = {
                        Button(onClick = apply, enabled = canApply) { Text("确认") }
                    }),
                )
            }
        }

        val field = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(PickerSemantics.FIELD)), useUnmergedTree = true)
        val popup = onNodeWithTag(PickerSemantics.POPUP)
        val opener = onNodeWithContentDescription("打开选择面板")
        val day = onNodeWithTag(PickerSemantics.day(selected))

        field.performClick().assertIsFocused()
        opener.performClick()
        day.performClick()
        day.performKeyInput { pressKey(Key.Escape) }
        popup.assertDoesNotExist()
        field.assertTextEquals("2026-11-25 09:30")
        runOnIdle { assertEquals(emptyList(), changes) }

        field.performClick()
        opener.performClick()
        day.performClick()
        onNodeWithText("确认").performClick()
        popup.assertDoesNotExist()
        field.assertTextEquals("2026-11-26 09:30").assertIsFocused()
        runOnIdle { assertEquals(listOf<LocalDateTime?>(selected.atTime(9, 30)), changes) }
    }
}
