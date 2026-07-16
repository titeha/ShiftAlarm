package ru.titeha.shiftalarm.alarm

import kotlin.random.Random

/**
 * Способ выключения звонка (жёсткий режим). «Отложить» работает всегда — задание усложняет ТОЛЬКО
 * «Стоп», а не запирает человека в звонке.
 */
enum class DismissMode {
    /** Обычное «Стоп». */
    NORMAL,

    /** Решить [MathChallenge.PROBLEM_COUNT] примера подряд. */
    MATH,

    /** Пройти [STEP_TARGET] шагов (TYPE_STEP_DETECTOR). */
    STEPS,

    /** Встряхнуть телефон [SHAKE_TARGET] раз. */
    SHAKE,
    ;

    companion object {
        const val STEP_TARGET = 20
        const val SHAKE_TARGET = 15
    }
}

/** Один пример «двузначное ± двузначное» без отрицательного результата. */
data class MathProblem(val a: Int, val b: Int, val plus: Boolean) {
    val answer: Int get() = if (plus) a + b else a - b
    val text: String get() = "$a ${if (plus) "+" else "−"} $b"
}

/**
 * Генерация примеров для жёсткого режима. Чистая (детерминирована переданным [Random]) — проверяется
 * unit-тестами. Уровень: двузначные слагаемые; при вычитании больший стоит первым (результат ≥ 0).
 */
object MathChallenge {
    const val PROBLEM_COUNT = 3

    fun generate(random: Random): List<MathProblem> = (0 until PROBLEM_COUNT).map {
        val x = random.nextInt(10, 100)
        val y = random.nextInt(10, 100)
        val plus = random.nextBoolean()
        if (!plus && y > x) MathProblem(y, x, false) else MathProblem(x, y, plus)
    }
}
