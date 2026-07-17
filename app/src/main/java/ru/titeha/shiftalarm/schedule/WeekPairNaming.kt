package ru.titeha.shiftalarm.schedule

/**
 * Как называть пару чередующихся учебных недель (вуз). Глобальная настройка приложения (не свойство
 * будильника): влияет только на подписи в мастере, календаре и превью. [odd] — для нечётной недели
 * ([Parity.ODD]), [even] — для чётной ([Parity.EVEN]).
 */
enum class WeekPairNaming(val odd: String, val even: String) {
    PARITY("Нечётная", "Чётная"),
    POSITION("Верхняя", "Нижняя"),
    FRACTION("Числитель", "Знаменатель");

    /** Подпись для конкретной чётности. */
    fun labelFor(parity: Parity): String = if (parity == Parity.ODD) odd else even
}
