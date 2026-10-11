package ru.titeha.shiftalarm.alarm

import kotlin.math.abs

/**
 * AUD-17 (BKL1): будильники, совпавшие по времени, звонят **одним** сигналом.
 *
 * Группа — рантайм-понятие: сущности в Room/списке/расписании остаются отдельными (по `alarmId`),
 * объединяется только показ и проигрывание на момент звонка. Чистое ядро без `android.*`.
 * Участники адресуемы по `alarmId` — задел под backlog-свайп (индивидуальный выход), который
 * в v1 не реализуется.
 */
data class RingGroupMember(
    val alarmId: Long,
    val label: String,
    val scheduledTriggerAtMillis: Long,
)

/** Порядок показа: по плановому времени, затем по `alarmId` — устойчивый. */
internal val ringGroupOrder: Comparator<RingGroupMember> =
    compareBy({ it.scheduledTriggerAtMillis }, { it.alarmId })

internal fun List<RingGroupMember>.sortedForGroup(): List<RingGroupMember> =
    sortedWith(ringGroupOrder)

/**
 * Можно ли присоединить [candidate] к активной группе [members] (сигнал уже играет)?
 *
 * Пустая группа — да (формируется новая). Иначе — если плановое время отличается не больше чем на
 * [joinWindowMillis] от времени группы (опорный участник — самый ранний). Значение окна берётся из
 * `AlarmFireValidator.DefaultTolerance`, третья константа не вводится.
 */
internal fun ringGroupCanJoin(
    members: List<RingGroupMember>,
    candidate: RingGroupMember,
    joinWindowMillis: Long,
): Boolean {
    val first = members.minByOrNull { it.scheduledTriggerAtMillis } ?: return true
    return abs(candidate.scheduledTriggerAtMillis - first.scheduledTriggerAtMillis) <= joinWindowMillis
}

/**
 * Добавить участника без дублей по `alarmId`; порядок — [sortedForGroup].
 * Идемпотентно: повторный участник с тем же `alarmId` заменяет прежнего и не ломает порядок.
 */
internal fun ringGroupWith(
    members: List<RingGroupMember>,
    candidate: RingGroupMember,
): List<RingGroupMember> =
    (members.filterNot { it.alarmId == candidate.alarmId } + candidate).sortedForGroup()

/**
 * Уникальные строки названий для экрана и уведомления: в порядке участников; пустая метка → одна
 * строка [defaultText]; повторяющиеся непустые названия — один раз (решение владельца 2026-10-11:
 * N одинаковых строк ничего не сообщают).
 */
internal fun ringGroupDisplayNames(
    members: List<RingGroupMember>,
    defaultText: String,
): List<String> =
    members.sortedForGroup()
        .map { it.label.trim().ifEmpty { defaultText } }
        .distinct()

/**
 * Однострочная сводка названий с усечением: при большем, чем [maxNames], числе строк — первые
 * [maxNames] − 1 и «и ещё N» (пример: «Подъём, Таблетки и ещё 2»).
 */
internal fun ringGroupSummary(
    members: List<RingGroupMember>,
    defaultText: String,
    maxNames: Int = 3,
): String {
    require(maxNames >= 2) { "maxNames должен быть ≥ 2" }
    val names = ringGroupDisplayNames(members, defaultText)
    if (names.size <= maxNames) return names.joinToString(", ")
    val shown = names.take(maxNames - 1)
    return shown.joinToString(", ") + " и ещё ${names.size - shown.size}"
}
