package ru.titeha.shiftalarm.ui

import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.schedule.ShiftCategory
import ru.titeha.shiftalarm.schedule.ShiftCycleCodec
import ru.titeha.shiftalarm.schedule.ShiftType
import java.time.LocalTime

/** Код проблемы, найденной перед сохранением редактора. */
enum class AlarmEditorIssueCode {
    WEEKLY_DAYS_REQUIRED,
    CUSTOM_CYCLE_INVALID,
    CUSTOM_CYCLE_EMPTY,
    CUSTOM_CYCLE_TOO_LONG,
    CUSTOM_CYCLE_RING_CONFLICT
}

/** Одна понятная пользователю проблема настроек будильника. */
data class AlarmEditorIssue(
    val code: AlarmEditorIssueCode,
    val message: String
)

/** Результат проверки текущего черновика редактора. */
data class AlarmEditorValidation(
    val issues: List<AlarmEditorIssue>
) {
    /** Сохранение разрешено только при отсутствии блокирующих проблем. */
    val canSave: Boolean
        get() = issues.isEmpty()
}

/**
 * Чистая проверка настроек редактора перед сохранением.
 *
 * Валидатор не зависит от Compose и Android API, поэтому его правила проверяются
 * обычными unit-тестами. UI остаётся последним рубежом: даже если отдельный контрол
 * временно позволит собрать некорректный черновик, сохранить его нельзя.
 */
object AlarmEditorValidator {
    /** Максимальная длина пользовательского сменного цикла после развёртки блоков. */
    const val MAX_CUSTOM_CYCLE_DAYS = 60

    /** Разовый будильник дополнительных правил пока не требует. */
    fun validateOnce(): AlarmEditorValidation {
        return AlarmEditorValidation(emptyList())
    }

    /** В режиме повтора должен быть выбран хотя бы один день недели. */
    fun validateWeekly(alarm: AlarmEntity): AlarmEditorValidation {
        if (alarm.daysMask != 0) {
            return AlarmEditorValidation(emptyList())
        }

        return AlarmEditorValidation(
            listOf(
                AlarmEditorIssue(
                    code = AlarmEditorIssueCode.WEEKLY_DAYS_REQUIRED,
                    message = "Для повтора выберите хотя бы один день недели."
                )
            )
        )
    }

    /**
     * Проверить сменный график.
     *
     * Встроенный пресет (`cycleSpec == null`) считается корректным. Для своего цикла
     * проверяются формат, непустой список, общий лимит и конфликт разных звонков,
     * которые одному циклу пришлось бы поставить в один календарный день.
     */
    fun validateShift(alarm: AlarmEntity): AlarmEditorValidation {
        val spec = alarm.cycleSpec ?: return AlarmEditorValidation(emptyList())
        val slots = ShiftCycleCodec.decodeOrNull(spec)
            ?: return AlarmEditorValidation(
                listOf(
                    AlarmEditorIssue(
                        code = AlarmEditorIssueCode.CUSTOM_CYCLE_INVALID,
                        message = "Пользовательский цикл повреждён. Выберите шаблон или создайте цикл заново."
                    )
                )
            )

        if (slots.isEmpty()) {
            return AlarmEditorValidation(
                listOf(
                    AlarmEditorIssue(
                        code = AlarmEditorIssueCode.CUSTOM_CYCLE_EMPTY,
                        message = "Добавьте в пользовательский цикл хотя бы один блок."
                    )
                )
            )
        }

        val issues = mutableListOf<AlarmEditorIssue>()

        if (slots.size > MAX_CUSTOM_CYCLE_DAYS) {
            issues += AlarmEditorIssue(
                code = AlarmEditorIssueCode.CUSTOM_CYCLE_TOO_LONG,
                message = "Пользовательский цикл содержит ${slots.size} дн. Максимум — $MAX_CUSTOM_CYCLE_DAYS."
            )
        }

        findFirstNightRingConflict(slots)?.let { conflict ->
            issues += AlarmEditorIssue(
                code = AlarmEditorIssueCode.CUSTOM_CYCLE_RING_CONFLICT,
                message = buildConflictMessage(conflict)
            )
        }

        return AlarmEditorValidation(issues)
    }

    /**
     * Найти первый случай, когда ночной звонок нельзя перенести накануне.
     *
     * Ночные слоты обрабатываются в том же порядке, что и нормализация в `AlarmTimes`:
     * если предыдущий день свободен, звонок переносится туда, а текущий слот очищается.
     * Если в предыдущем дне уже есть звонок на другое время, одному циклу понадобились бы
     * два разных звонка в одни сутки — текущая модель этого не поддерживает.
     *
     * Совпадающее время конфликтом не считается: один физический сигнал может выполнить
     * обе причины звонка. Это также сохраняет совместимость с нормализованными шаблонами.
     */
    private fun findFirstNightRingConflict(slots: List<ShiftType>): NightRingConflict? {
        val normalized = slots.toMutableList()

        for (index in slots.indices) {
            val sourceSlot = slots[index]
            val nightTime = sourceSlot.wakeTime ?: continue

            if (sourceSlot.category != ShiftCategory.NIGHT) {
                continue
            }

            val previousIndex = if (index == 0) slots.lastIndex else index - 1
            val previousSlot = normalized[previousIndex]
            val existingTime = previousSlot.wakeTime

            if (existingTime == null) {
                normalized[previousIndex] = previousSlot.copy(wakeTime = nightTime)
                normalized[index] = normalized[index].copy(wakeTime = null)
                continue
            }

            if (existingTime != nightTime) {
                return NightRingConflict(
                    nightName = sourceSlot.name,
                    nightTime = nightTime,
                    previousDayIndex = previousIndex,
                    existingTime = existingTime
                )
            }
        }

        return null
    }

    private fun buildConflictMessage(conflict: NightRingConflict): String {
        val nightName = conflict.nightName.ifBlank { "Ночная смена" }

        return "${nightName}: звонок ${conflict.nightTime.asClock()} должен быть накануне, " +
            "но в дне ${conflict.previousDayIndex + 1} цикла уже есть звонок " +
            "${conflict.existingTime.asClock()}. Один цикл пока не поддерживает " +
            "два разных звонка в одни сутки."
    }

    private fun LocalTime.asClock(): String {
        return "%02d:%02d".format(hour, minute)
    }

    private data class NightRingConflict(
        val nightName: String,
        val nightTime: LocalTime,
        val previousDayIndex: Int,
        val existingTime: LocalTime
    )
}
