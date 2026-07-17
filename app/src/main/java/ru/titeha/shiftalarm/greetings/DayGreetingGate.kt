package ru.titeha.shiftalarm.greetings

import java.time.LocalDate

/**
 * Чистое решение: показывать ли карточку «Настроение дня» на главной сегодня.
 *
 * Карточка появляется после реального «Стоп» (сигнал [lastDismissedEpochDay]) и только если:
 *  - опция включена ([cardEnabled]);
 *  - будильник выключали ИМЕННО СЕГОДНЯ (после снуза/пропуска сигнал не пишется — см.
 *    RingSessionController), поэтому «мы будильник, а не календарь»: без звонка карточки нет;
 *  - карточку сегодня ещё не закрывали ([cardHandledEpochDay]);
 *  - есть что показать ([greeting] не пуст).
 */
object DayGreetingGate {
  fun shouldShowCard(
    cardEnabled: Boolean,
    today: LocalDate,
    lastDismissedEpochDay: Long,
    cardHandledEpochDay: Long,
    greeting: Greeting,
  ): Boolean {
    val todayEpoch = today.toEpochDay()
    return cardEnabled &&
      lastDismissedEpochDay == todayEpoch &&
      cardHandledEpochDay != todayEpoch &&
      !greeting.isEmpty
  }
}
