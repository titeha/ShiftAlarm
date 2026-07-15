package ru.titeha.shiftalarm.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftCycleEditLimitsTest {
    @Test
    fun `в пустой цикл можно добавить блок`() {
        assertTrue(ShiftCycleEditLimits.canAddBlock(0))
    }

    @Test
    fun `в цикл на пределе нельзя добавить блок`() {
        assertFalse(
            ShiftCycleEditLimits.canAddBlock(
                AlarmEditorValidator.MAX_CUSTOM_CYCLE_DAYS
            )
        )
    }

    @Test
    fun `для нового блока в цикле на 59 дней доступен один день`() {
        assertEquals(
            1,
            ShiftCycleEditLimits.maxBlockCount(
                AlarmEditorValidator.MAX_CUSTOM_CYCLE_DAYS - 1
            )
        )
    }

    @Test
    fun `размер блока ограничен тридцатью днями`() {
        assertEquals(
            ShiftCycleEditLimits.MAX_BLOCK_DAYS,
            ShiftCycleEditLimits.maxBlockCount(otherDays = 0)
        )
    }

    @Test
    fun `при редактировании учитывается место исходного блока`() {
        /*
         * В цикле 59 дней редактируется блок из 10 дней.
         * В остальных блоках 49 дней, значит блок можно увеличить до 11.
         */
        assertEquals(
            11,
            ShiftCycleEditLimits.maxBlockCount(otherDays = 49)
        )
    }

    @Test
    fun `блок можно увеличить до общего предела`() {
        assertTrue(
            ShiftCycleEditLimits.canUseBlockCount(
                otherDays = 49,
                count = 11
            )
        )
    }

    @Test
    fun `блок нельзя увеличить сверх общего предела`() {
        assertFalse(
            ShiftCycleEditLimits.canUseBlockCount(
                otherDays = 49,
                count = 12
            )
        )
    }

    @Test
    fun `однодневный блок можно дублировать до ровно шестидесяти дней`() {
        /*
         * Остальные блоки занимают 58 дней.
         * Исходный день + копия дают итоговые 60 дней.
         */
        assertTrue(
            ShiftCycleEditLimits.canDuplicate(
                otherDays = 58,
                editedCount = 1
            )
        )
    }

    @Test
    fun `дублирование блокируется если копия превышает лимит`() {
        /*
         * Остальные блоки занимают 57 дней.
         * Два блока по 2 дня дали бы 61 день.
         */
        assertFalse(
            ShiftCycleEditLimits.canDuplicate(
                otherDays = 57,
                editedCount = 2
            )
        )
    }

    @Test
    fun `дублирование учитывает изменённый размер блока`() {
        /*
         * Исходный блок мог иметь другой размер. Проверяется именно локальный
         * размер, который пользователь подготовил в bottom sheet.
         */
        assertTrue(
            ShiftCycleEditLimits.canDuplicate(
                otherDays = 50,
                editedCount = 5
            )
        )

        assertFalse(
            ShiftCycleEditLimits.canDuplicate(
                otherDays = 50,
                editedCount = 6
            )
        )
    }

    @Test
    fun `нулевой размер блока недопустим`() {
        assertFalse(
            ShiftCycleEditLimits.canUseBlockCount(
                otherDays = 10,
                count = 0
            )
        )

        assertFalse(
            ShiftCycleEditLimits.canDuplicate(
                otherDays = 10,
                editedCount = 0
            )
        )
    }
}
