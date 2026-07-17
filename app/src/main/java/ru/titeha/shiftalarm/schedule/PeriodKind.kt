package ru.titeha.shiftalarm.schedule

/**
 * Тип периода без будильника (для подписи и цвета на календаре). Хранится в `AlarmPeriod.reason`
 * как [label] (без отдельной колонки/миграции). Чистая логика, без Android.
 */
enum class PeriodKind(val label: String) {
  VACATION("Отпуск"),
  SICK("Больничный"),
  DAYOFF("Отгул"),
  UNPAID("За свой счёт"),
  SCHOOL_BREAK("Каникулы"),
  SESSION("Сессия");

  companion object {
    /** Тип по строке причины; неизвестное/старое значение → [VACATION]. */
    fun fromReason(reason: String): PeriodKind =
      entries.firstOrNull { it.label.equals(reason.trim(), ignoreCase = true) } ?: VACATION
  }
}
