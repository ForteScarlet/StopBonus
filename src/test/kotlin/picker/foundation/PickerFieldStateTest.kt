package picker.foundation

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import picker.core.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import kotlin.test.*

class PickerFieldStateTest {
    private val environment = PickerEnvironment(
        clock = Clock.fixed(Instant.parse("2026-09-06T01:00:00Z"), ZoneOffset.UTC),
        zoneId = ZoneOffset.UTC,
    )
    private val now = environment.sampleNow()
    private val dateCodec = PickerCodecs.date()

    @Test
    fun directSubmitEvaluatesCurrentTextAndRequestsOnlyOnce() {
        val callbacks = mutableListOf<LocalDate?>()
        val state = PickerFieldState<LocalDate>()
        val owner = Any()
        state.bind(owner, dateConfiguration(null), callbacks::add)

        state.updateInput(TextFieldValue("2026-9-6", TextRange(8)))

        assertEquals(
            SubmitEvaluation.Ready(LocalDate.of(2026, 9, 6)),
            state.evaluateForSubmit(now),
        )
        assertTrue(state.isDirty)
        assertTrue(state.submitDirect(now) is SubmitEvaluation.Ready)
        assertEquals(listOf(LocalDate.of(2026, 9, 6)), callbacks)

        state.bind(owner, dateConfiguration(LocalDate.of(2026, 9, 6)), callbacks::add)
        assertFalse(state.isDirty)
        assertEquals("2026-09-06", state.inputValue.text)
        state.unbind(owner)
    }

    @Test
    fun externalUpdateWhileEditingCreatesConflictAndLoadLatestResetsDraft() {
        val state = PickerFieldState<LocalDate>()
        val owner = Any()
        state.bind(owner, dateConfiguration(LocalDate.of(2026, 9, 6)), {})
        state.updateInput(TextFieldValue("2026-9-7", TextRange(8)))

        state.bind(owner, dateConfiguration(LocalDate.of(2026, 9, 8)), {})

        assertEquals(PickerIssueCodes.ExternalConflict, state.issue?.code)
        assertIs<SubmitEvaluation.Blocked>(state.evaluateForSubmit(now))

        state.loadExternalValue()

        assertNull(state.issue)
        assertFalse(state.isDirty)
        assertEquals("2026-09-08", state.inputValue.text)
        state.unbind(owner)
    }

    @Test
    fun cancelRestoresDirectSnapshotAndDoesNotCallBusinessCallback() {
        val callbacks = mutableListOf<LocalDate?>()
        val state = PickerFieldState<LocalDate>()
        val owner = Any()
        state.bind(owner, dateConfiguration(LocalDate.of(2026, 9, 6)), callbacks::add)
        state.updateInput(TextFieldValue("2026-9-", TextRange(7)))

        state.openPanel()
        state.updatePanelDraft(DateDraft(TextFieldValue("2026-09-10", TextRange(10))))
        state.closePanelWithoutApplying()

        assertEquals("2026-9-", state.inputValue.text)
        assertTrue(state.isDirty)
        assertTrue(callbacks.isEmpty())
        state.unbind(owner)
    }

    @Test
    fun openingPanelKeepsUnfinishedDirectTextInsteadOfRestoringTheOldValue() {
        val state = PickerFieldState<LocalDate>()
        val owner = Any()
        state.bind(owner, dateConfiguration(LocalDate.of(2026, 9, 6)), {})
        state.updateInput(TextFieldValue("2026-9-", TextRange(7)))

        state.openPanel()

        assertEquals("2026-9-", state.panelRawInput?.text)
        assertEquals("", (state.panelDraft as DateDraft).input.text)
        state.unbind(owner)
    }

    @Test
    fun resetWaitsForNextExternalBindInsteadOfReturningOldValue() {
        val state = PickerFieldState<LocalDate>()
        val owner = Any()
        state.bind(owner, dateConfiguration(LocalDate.of(2026, 9, 6)), {})
        state.resetEditing()

        assertIs<SubmitEvaluation.Blocked>(state.evaluateForSubmit(now))

        state.bind(owner, dateConfiguration(null), {})

        assertEquals("", state.inputValue.text)
        assertFalse(state.isDirty)
        assertEquals(
            SubmitEvaluation.Ready(null),
            state.evaluateForSubmit(now),
        )
        state.unbind(owner)
    }

    private fun dateConfiguration(value: LocalDate?): FieldConfiguration<LocalDate> =
        FieldConfiguration(
            kind = PickerFieldKind.Date,
            contextKey = "test-date",
            value = value,
            required = false,
            formatValue = { dateCodec.format(it, Locale.ROOT) },
            parseText = { dateCodec.parse(it, Locale.ROOT) },
            createDraft = { DateDraft.from(it, dateCodec, Locale.ROOT) },
            evaluateText = { input, original, currentNow, _ ->
                evaluateDate(dateCodec.parse(input.text, Locale.ROOT), currentNow)
            },
            evaluateDraft = { draft, _, currentNow, _ ->
                val result = (draft as? DateDraft)?.let { resolveDraft(it, dateCodec, Locale.ROOT) }
                    ?: ParseResult.Empty
                evaluateDate(result, currentNow)
            },
            validateExternal = { date, _ -> SubmitEvaluation.Ready(date) },
        )

    private fun evaluateDate(
        result: ParseResult<LocalDate>,
        now: PickerNow,
    ): SubmitEvaluation<LocalDate> = when (result) {
        ParseResult.Empty -> SubmitEvaluation.Ready(null)
        ParseResult.Incomplete -> SubmitEvaluation.Blocked(
            picker.core.PickerIssue(PickerIssueCodes.Incomplete),
        )

        is ParseResult.Invalid -> SubmitEvaluation.Blocked(result.issue)
        is ParseResult.Parsed -> when (
            val validation = picker.core.validateDate(result.value, DateConstraints(), now)
        ) {
            ValidationResult.Valid -> SubmitEvaluation.Ready(result.value)
            is ValidationResult.Invalid -> SubmitEvaluation.Blocked(validation.issue)
        }
    }
}
