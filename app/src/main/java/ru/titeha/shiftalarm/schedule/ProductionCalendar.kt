package ru.titeha.shiftalarm.schedule

import java.time.DayOfWeek
import java.time.LocalDate

/** Официальный статус календарного дня по производственному календарю. */
enum class DayStatus { WORKING, NONWORKING }

/**
 * Тип особого дня государственного календаря. Разводит две оси, которые булев «выходной» склеивал:
 * праздник касается ВСЕХ, а перенос имеет смысл только для стандартной недели страны.
 *
 *  - [NONE]          — обычный день (статус решает рабочая неделя пользователя);
 *  - [HOLIDAY]       — праздник (нерабочий для всех, независимо от недели);
 *  - [TRANSFER_OFF]  — перенесённый на будень выходной (нерабочий ТОЛЬКО для стандартной недели);
 *  - [TRANSFER_WORK] — перенос в рабочий день (рабочая суббота: рабочий для стандартной недели);
 *  - [PRE_HOLIDAY]   — сокращённый предпраздничный (рабочий, подъём тот же);
 *  - [UNKNOWN]       — данных на дату нет (ведёт себя как [NONE]).
 */
enum class StateDayKind { NONE, HOLIDAY, TRANSFER_OFF, TRANSFER_WORK, PRE_HOLIDAY, UNKNOWN }

/** Человекочитаемая причина особого дня (для подписи в календаре). null — обычный день. */
fun stateDayKindLabel(kind: StateDayKind): String? = when (kind) {
    StateDayKind.HOLIDAY -> "Праздник"
    StateDayKind.TRANSFER_OFF -> "Перенесённый выходной"
    StateDayKind.TRANSFER_WORK -> "Рабочий день (перенос)"
    StateDayKind.PRE_HOLIDAY -> "Сокращённый день"
    StateDayKind.NONE, StateDayKind.UNKNOWN -> null
}

/**
 * Производственный календарь: официальный статус каждого дня с учётом праздников и переносов
 * выходных. Чистая логика (без Android/I-O); данные по стране/году — в [ProductionCalendars].
 *
 * Типизированный слой [kinds] задаёт точный [StateDayKind] по дате. Для обратной совместимости
 * (и как грубый источник) остаются булевы наборы [holidays]/[workingWeekends]: если для даты нет
 * явного kind, он выводится из них (праздник/перенос-рабочий), иначе — обычный день. По умолчанию
 * суббота и воскресенье — нерабочие (базовая пятидневка; настраиваемая рабочая неделя — отдельный шаг).
 */
class ProductionCalendar(
  val holidays: Set<LocalDate> = emptySet(),
  val workingWeekends: Set<LocalDate> = emptySet(),
  val kinds: Map<LocalDate, StateDayKind> = emptyMap(),
) {

  /** Тип особого дня: явный из [kinds], иначе выведенный из булевых наборов, иначе [StateDayKind.NONE]. */
  fun kindOf(date: LocalDate): StateDayKind = kinds[date] ?: when {
    date in workingWeekends -> StateDayKind.TRANSFER_WORK
    date in holidays -> StateDayKind.HOLIDAY
    else -> StateDayKind.NONE
  }

  fun isNonWorking(date: LocalDate): Boolean = when (kindOf(date)) {
    StateDayKind.HOLIDAY, StateDayKind.TRANSFER_OFF -> true   // праздник / перенесённый выходной
    StateDayKind.TRANSFER_WORK -> false                       // рабочая суббота (перенос)
    // Обычный/сокращённый/неизвестный день — по базовой рабочей неделе (пока Сб/Вс — выходные).
    else -> date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
  }

  fun isWorking(date: LocalDate): Boolean = !isNonWorking(date)

  fun status(date: LocalDate): DayStatus =
    if (isNonWorking(date)) DayStatus.NONWORKING else DayStatus.WORKING
}
