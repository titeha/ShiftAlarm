package ru.titeha.shiftalarm.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.schedule.AlarmTimes
import ru.titeha.shiftalarm.schedule.ShiftCategory
import ru.titeha.shiftalarm.schedule.ShiftCycleCodec
import ru.titeha.shiftalarm.schedule.ShiftType
import java.time.DayOfWeek
import java.time.LocalTime

class AlarmEditorValidatorTest {
    @Test
    fun `разовый будильник проходит проверку`() {
        assertTrue(AlarmEditorValidator.validateOnce().canSave)
    }

    @Test
    fun `повтор без дней недели блокирует сохранение`() {
        val result = AlarmEditorValidator.validateWeekly(
            AlarmEntity(daysMask = 0)
        )

        assertFalse(result.canSave)
        assertTrue(result.has(AlarmEditorIssueCode.WEEKLY_DAYS_REQUIRED))
    }

    @Test
    fun `повтор с выбранным днём проходит проверку`() {
        val result = AlarmEditorValidator.validateWeekly(
            AlarmEntity(daysMask = AlarmTimes.maskOf(DayOfWeek.MONDAY))
        )

        assertTrue(result.canSave)
    }

    @Test
    fun `встроенный сменный пресет проходит проверку`() {
        val result = AlarmEditorValidator.validateShift(
            AlarmEntity(
                mode = AlarmEntity.MODE_SHIFT,
                cycleSpec = null
            )
        )

        assertTrue(result.canSave)
    }

    @Test
    fun `повреждённый пользовательский цикл блокирует сохранение`() {
        val result = AlarmEditorValidator.validateShift(
            AlarmEntity(
                mode = AlarmEntity.MODE_SHIFT,
                cycleSpec = "сломанный слот"
            )
        )

        assertFalse(result.canSave)
        assertTrue(result.has(AlarmEditorIssueCode.CUSTOM_CYCLE_INVALID))
    }

    @Test
    fun `пустой пользовательский цикл блокирует сохранение`() {
        val result = AlarmEditorValidator.validateShift(
            AlarmEntity(
                mode = AlarmEntity.MODE_SHIFT,
                cycleSpec = ""
            )
        )

        assertFalse(result.canSave)
        assertTrue(result.has(AlarmEditorIssueCode.CUSTOM_CYCLE_EMPTY))
    }

    @Test
    fun `цикл длиннее лимита блокирует сохранение`() {
        val slots = List(AlarmEditorValidator.MAX_CUSTOM_CYCLE_DAYS + 1) { index ->
            ShiftType(
                id = "d$index",
                name = "День",
                wakeTime = null,
                category = ShiftCategory.DAY
            )
        }

        val result = AlarmEditorValidator.validateShift(
            customAlarm(slots)
        )

        assertFalse(result.canSave)
        assertTrue(result.has(AlarmEditorIssueCode.CUSTOM_CYCLE_TOO_LONG))
    }

    @Test
    fun `цикл ровно на лимите проходит проверку`() {
        val slots = List(AlarmEditorValidator.MAX_CUSTOM_CYCLE_DAYS) { index ->
            ShiftType(
                id = "d$index",
                name = "День",
                wakeTime = null,
                category = ShiftCategory.DAY
            )
        }

        val result = AlarmEditorValidator.validateShift(
            customAlarm(slots)
        )

        assertTrue(result.canSave)
    }

    @Test
    fun `последовательные ночные смены с разным временем не конфликтуют`() {
        val slots = listOf(
            off(),
            night("Ночь 1", 21, 0),
            night("Ночь 2", 22, 0),
            off()
        )

        val result = AlarmEditorValidator.validateShift(
            customAlarm(slots)
        )

        assertTrue(result.canSave)
    }

    @Test
    fun `два разных звонка одного цикла в одни сутки блокируют сохранение`() {
        val slots = listOf(
            day("Дневная смена", 7, 0),
            night("Ночная смена", 21, 0),
            off()
        )

        val result = AlarmEditorValidator.validateShift(
            customAlarm(slots)
        )

        assertFalse(result.canSave)
        assertTrue(result.has(AlarmEditorIssueCode.CUSTOM_CYCLE_RING_CONFLICT))
    }

    @Test
    fun `совпадающее время в предыдущем дне не считается конфликтом`() {
        val slots = listOf(
            day("Дневная смена", 21, 0),
            night("Ночная смена", 21, 0),
            off()
        )

        val result = AlarmEditorValidator.validateShift(
            customAlarm(slots)
        )

        assertTrue(result.canSave)
    }

    @Test
    fun `конфликт учитывает переход через конец цикла`() {
        val slots = listOf(
            night("Ночная смена", 21, 0),
            off(),
            day("Последний день цикла", 7, 0)
        )

        val result = AlarmEditorValidator.validateShift(
            customAlarm(slots)
        )

        assertFalse(result.canSave)
        assertTrue(result.has(AlarmEditorIssueCode.CUSTOM_CYCLE_RING_CONFLICT))
    }

    @Test
    fun `нормализованная цепочка ночей из шаблона не считается конфликтом`() {
        val slots = listOf(
            ShiftType(
                id = "depart",
                name = "Выходной",
                wakeTime = LocalTime.of(21, 0),
                category = ShiftCategory.OFF
            ),
            night("Ночь 1", 21, 0),
            night("Ночь 2", 21, 0),
            ShiftType(
                id = "night-last",
                name = "Ночь 3",
                wakeTime = null,
                category = ShiftCategory.NIGHT
            ),
            off()
        )

        val result = AlarmEditorValidator.validateShift(
            customAlarm(slots)
        )

        assertTrue(result.canSave)
    }

    private fun AlarmEditorValidation.has(code: AlarmEditorIssueCode): Boolean {
        return issues.any { it.code == code }
    }

    private fun customAlarm(slots: List<ShiftType>): AlarmEntity {
        return AlarmEntity(
            mode = AlarmEntity.MODE_SHIFT,
            cycleSpec = ShiftCycleCodec.encode(slots)
        )
    }

    private fun day(name: String, hour: Int, minute: Int): ShiftType {
        return ShiftType(
            id = "day-$name",
            name = name,
            wakeTime = LocalTime.of(hour, minute),
            category = ShiftCategory.DAY
        )
    }

    private fun night(name: String, hour: Int, minute: Int): ShiftType {
        return ShiftType(
            id = "night-$name",
            name = name,
            wakeTime = LocalTime.of(hour, minute),
            category = ShiftCategory.NIGHT
        )
    }

    private fun off(): ShiftType {
        return ShiftType.off()
    }
}
