package ru.titeha.shiftalarm.ui

/**
 * Ограничения операций редактора пользовательского сменного цикла.
 *
 * Правила вынесены из Compose, чтобы увеличение и дублирование блоков
 * проверялись обычными unit-тестами.
 */
object ShiftCycleEditLimits {
    /** Максимальное число одинаковых дней внутри одного блока. */
    const val MAX_BLOCK_DAYS = 30

    /** Можно ли добавить в цикл новый блок хотя бы из одного дня. */
    fun canAddBlock(totalDays: Int): Boolean {
        return totalDays >= 0 &&
            totalDays < AlarmEditorValidator.MAX_CUSTOM_CYCLE_DAYS
    }

    /**
     * Максимальное число дней для редактируемого блока.
     *
     * [otherDays] — число дней во всех остальных блоках. Для нового блока это
     * полная текущая длина цикла.
     */
    fun maxBlockCount(otherDays: Int): Int {
        require(otherDays >= 0) {
            "Число дней в остальных блоках не может быть отрицательным."
        }

        val remaining = (
            AlarmEditorValidator.MAX_CUSTOM_CYCLE_DAYS - otherDays
        ).coerceAtLeast(0)

        return minOf(MAX_BLOCK_DAYS, remaining)
    }

    /** Помещается ли блок указанного размера в общий лимит цикла. */
    fun canUseBlockCount(otherDays: Int, count: Int): Boolean {
        if (otherDays < 0 || count <= 0) {
            return false
        }

        return count <= maxBlockCount(otherDays)
    }

    /**
     * Можно ли сохранить изменённый блок и сразу создать рядом его копию.
     *
     * Итоговая длина равна:
     *
     * `остальные дни + изменённый блок + его копия`.
     */
    fun canDuplicate(otherDays: Int, editedCount: Int): Boolean {
        if (!canUseBlockCount(otherDays, editedCount)) {
            return false
        }

        val resultDays =
            otherDays.toLong() + editedCount.toLong() * 2L

        return resultDays <=
            AlarmEditorValidator.MAX_CUSTOM_CYCLE_DAYS.toLong()
    }
}
