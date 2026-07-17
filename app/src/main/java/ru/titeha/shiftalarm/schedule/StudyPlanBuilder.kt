package ru.titeha.shiftalarm.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Чётность учебной недели (вуз). Считать из номера календарной недели НЕЛЬЗЯ — вузы считают по-разному. */
enum class Parity { ODD, EVEN }

/** Готовый учебный план: слоты цикла (7 или 14) + опорная дата (всегда понедельник). */
data class StudyPlan(val slots: List<ShiftType>, val anchorDate: LocalDate)

/**
 * Чистый сборщик учебного плана (без Android). Учёба — ШАБЛОН поверх сменного цикла: школа/«одинаковая
 * неделя» = 7 слотов, вуз/«две недели» = 14 слотов (неделя А = 0–6, неделя Б = 7–13). Границы недель
 * цикла — всегда понедельник (настройка отображения «Начало недели» их не двигает).
 *
 * Слоты — существующие типы: подъём → «День» ([ShiftCategory.DAY]), пусто → «Выходной». Новых типов
 * слотов и режимов не вводим — на выходе обычный `cycleSpec` сменного будильника.
 */
object StudyPlanBuilder {

    /**
     * @param grid 7 (одна неделя) или 14 (две недели) значений; null = выходной.
     * @param parityToday чётность ТЕКУЩЕЙ недели для 14-дневного (по ответу пользователя); null для 7.
     * @param today дата создания — определяет опорный понедельник.
     * @throws IllegalArgumentException при неверном размере сетки, пустой сетке или отсутствии чётности.
     */
    fun build(grid: List<LocalTime?>, parityToday: Parity?, today: LocalDate): StudyPlan {
        require(grid.size == 7 || grid.size == 14) {
            "Сетка должна быть 7 или 14 дней, получено ${grid.size}"
        }
        require(grid.any { it != null }) {
            "Нужен хотя бы один учебный день с подъёмом (пустой план не сохраняем)"
        }
        val twoWeeks = grid.size == 14
        require(!twoWeeks || parityToday != null) {
            "Для двухнедельного цикла нужна чётность текущей недели"
        }

        val slots = grid.map { time ->
            if (time == null) ShiftType.off() else ShiftType("study", "Учёба", time, ShiftCategory.DAY)
        }

        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // Нечётная → сегодня в неделе А (якорь = понедельник этой недели);
        // чётная → сегодня в неделе Б (якорь на неделю раньше, чтобы сегодня попало в слоты 7–13).
        val anchor = when {
            !twoWeeks -> thisMonday
            parityToday == Parity.ODD -> thisMonday
            else -> thisMonday.minusWeeks(1)
        }

        return StudyPlan(slots, anchor)
    }
}
