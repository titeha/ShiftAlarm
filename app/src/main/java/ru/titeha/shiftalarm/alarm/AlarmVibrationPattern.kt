package ru.titeha.shiftalarm.alarm

/**
 * Шаблон вибрации для сработавшего будильника.
 *
 * Нечётные элементы массива — интервалы вибрации, чётные — паузы.
 * Первый ноль означает: начать сразу, без начальной задержки.
 */
object AlarmVibrationPattern {
  private val PatternMillis = longArrayOf(
    0L,
    700L,
    500L,
    700L,
    1_200L
  )

  private const val RepeatIndex = 0

  /** Копия шаблона, чтобы вызывающий код не мог изменить общий массив. */
  fun timingsMillis(): LongArray {
    return PatternMillis.copyOf()
  }

  /** Индекс, с которого шаблон повторяется. */
  fun repeatIndex(): Int {
    return RepeatIndex
  }

  /** Самопроверка шаблона для unit-теста. */
  fun isValid(): Boolean {
    return PatternMillis.isNotEmpty() &&
            RepeatIndex in PatternMillis.indices &&
            PatternMillis.all { it >= 0L } &&
            PatternMillis.drop(1).any { it > 0L }
  }
}