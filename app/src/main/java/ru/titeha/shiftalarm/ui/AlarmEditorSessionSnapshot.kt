package ru.titeha.shiftalarm.ui

import ru.titeha.shiftalarm.data.AlarmEntity
import ru.titeha.shiftalarm.data.AlarmOverride
import ru.titeha.shiftalarm.data.AlarmPeriod
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Компактный снимок сессии редактора для восстановления после уничтожения процесса.
 *
 * Хранятся и исходные данные, и текущий черновик. Исходные данные нужны, чтобы
 * после восстановления правильно продолжила работать проверка несохранённых изменений.
 */
internal data class AlarmEditorSessionSnapshot(
    val initialAlarm: AlarmEntity,
    val initialPeriods: List<AlarmPeriod>,
    val initialOverrides: List<AlarmOverride>,
    val currentAlarm: AlarmEntity,
    val currentPeriods: List<AlarmPeriod>,
    val currentOverrides: List<AlarmOverride>,
    val currentMethod: EditMethod,
    val discardDialogVisible: Boolean
)

/**
 * Версионированный кодек снимка редактора.
 *
 * Формат бинарный и затем кодируется URL-safe Base64. Он не зависит от Android API,
 * поэтому round-trip и повреждённые данные проверяются обычными unit-тестами.
 *
 * Снимок намеренно ограничен по размеру. SavedState предназначен для небольшого
 * пользовательского состояния, а не для хранения произвольно больших наборов данных.
 */
internal object AlarmEditorSessionSnapshotCodec {
    private const val FORMAT_VERSION = 1

    /** Около 96 КиБ до Base64 и около 128 КиБ после кодирования. */
    private const val MAX_RAW_BYTES = 96 * 1024
    private const val MAX_ENCODED_CHARS = 128 * 1024

    private const val MAX_STRING_BYTES = 64 * 1024
    private const val MAX_CHILD_RECORDS = 1_000

    fun encodeOrNull(
        snapshot: AlarmEditorSessionSnapshot
    ): String? {
        return try {
            val buffer = ByteArrayOutputStream()

            DataOutputStream(buffer).use { output ->
                output.writeInt(FORMAT_VERSION)

                output.writeAlarm(snapshot.initialAlarm)
                output.writePeriods(snapshot.initialPeriods)
                output.writeOverrides(snapshot.initialOverrides)

                output.writeAlarm(snapshot.currentAlarm)
                output.writePeriods(snapshot.currentPeriods)
                output.writeOverrides(snapshot.currentOverrides)

                output.writeStringValue(
                    snapshot.currentMethod.name
                )
                output.writeBoolean(
                    snapshot.discardDialogVisible
                )
            }

            val bytes = buffer.toByteArray()

            if (bytes.size > MAX_RAW_BYTES) {
                null
            } else {
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytes)
                    .takeIf {
                        it.length <= MAX_ENCODED_CHARS
                    }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeOrNull(
        encoded: String
    ): AlarmEditorSessionSnapshot? {
        if (
            encoded.isBlank() ||
            encoded.length > MAX_ENCODED_CHARS
        ) {
            return null
        }

        return try {
            val bytes = Base64.getUrlDecoder()
                .decode(encoded)

            require(bytes.size <= MAX_RAW_BYTES) {
                "Снимок редактора слишком велик."
            }

            DataInputStream(
                ByteArrayInputStream(bytes)
            ).use { input ->
                require(
                    input.readInt() == FORMAT_VERSION
                ) {
                    "Неподдерживаемая версия снимка."
                }

                val snapshot =
                    AlarmEditorSessionSnapshot(
                        initialAlarm =
                            input.readAlarm(),
                        initialPeriods =
                            input.readPeriods(),
                        initialOverrides =
                            input.readOverrides(),
                        currentAlarm =
                            input.readAlarm(),
                        currentPeriods =
                            input.readPeriods(),
                        currentOverrides =
                            input.readOverrides(),
                        currentMethod =
                            EditMethod.valueOf(
                                input.readStringValue()
                            ),
                        discardDialogVisible =
                            input.readBoolean()
                    )

                require(input.available() == 0) {
                    "После снимка остались лишние данные."
                }

                snapshot
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun DataOutputStream.writeAlarm(
        alarm: AlarmEntity
    ) {
        writeLong(alarm.id)
        writeStringValue(alarm.label)
        writeBoolean(alarm.enabled)
        writeInt(alarm.hour)
        writeInt(alarm.minute)
        writeStringValue(alarm.mode)
        writeInt(alarm.daysMask)
        writeStringValue(alarm.presetId)
        writeLong(alarm.anchorEpochDay)
        writeNullableString(alarm.cycleSpec)
        writeBoolean(alarm.deleteAfterFiring)
        writeBoolean(alarm.freezeCycleDuringOff)
        writeBoolean(alarm.honorHolidays)
        writeStringValue(alarm.polarity)
    }

    private fun DataInputStream.readAlarm(): AlarmEntity {
        val alarm = AlarmEntity(
            id = readLong(),
            label = readStringValue(),
            enabled = readBoolean(),
            hour = readInt(),
            minute = readInt(),
            mode = readStringValue(),
            daysMask = readInt(),
            presetId = readStringValue(),
            anchorEpochDay = readLong(),
            cycleSpec = readNullableString(),
            deleteAfterFiring = readBoolean(),
            freezeCycleDuringOff = readBoolean(),
            honorHolidays = readBoolean(),
            polarity = readStringValue()
        )

        require(alarm.id >= 0L) {
            "Некорректный id будильника."
        }

        require(alarm.hour in 0..23) {
            "Некорректный час будильника."
        }

        require(alarm.minute in 0..59) {
            "Некорректные минуты будильника."
        }

        require(
            alarm.mode == AlarmEntity.MODE_WEEKLY ||
                alarm.mode == AlarmEntity.MODE_SHIFT
        ) {
            "Некорректный режим будильника."
        }

        require(alarm.daysMask in 0..0b1111111) {
            "Некорректная маска дней недели."
        }

        require(
            alarm.polarity ==
                AlarmEntity.POLARITY_WORK ||
                alarm.polarity ==
                AlarmEntity.POLARITY_REST
        ) {
            "Некорректная полярность будильника."
        }

        return alarm
    }

    private fun DataOutputStream.writePeriods(
        periods: List<AlarmPeriod>
    ) {
        require(periods.size <= MAX_CHILD_RECORDS) {
            "Слишком много периодов."
        }

        writeInt(periods.size)

        periods.forEach { period ->
            writeLong(period.id)
            writeLong(period.alarmId)
            writeLong(period.fromEpochDay)
            writeLong(period.toEpochDay)
            writeStringValue(period.reason)
        }
    }

    private fun DataInputStream.readPeriods():
        List<AlarmPeriod> {
        val count = readCollectionSize(
            "периодов"
        )

        return List(count) {
            AlarmPeriod(
                id = readLong(),
                alarmId = readLong(),
                fromEpochDay = readLong(),
                toEpochDay = readLong(),
                reason = readStringValue()
            )
        }
    }

    private fun DataOutputStream.writeOverrides(
        overrides: List<AlarmOverride>
    ) {
        require(overrides.size <= MAX_CHILD_RECORDS) {
            "Слишком много правок."
        }

        writeInt(overrides.size)

        overrides.forEach { override ->
            writeLong(override.id)
            writeLong(override.alarmId)
            writeLong(override.fromEpochDay)
            writeLong(override.toEpochDay)
            writeStringValue(override.category)
            writeNullableInt(override.wakeMinutes)
            writeStringValue(override.name)
        }
    }

    private fun DataInputStream.readOverrides():
        List<AlarmOverride> {
        val count = readCollectionSize(
            "правок"
        )

        return List(count) {
            AlarmOverride(
                id = readLong(),
                alarmId = readLong(),
                fromEpochDay = readLong(),
                toEpochDay = readLong(),
                category = readStringValue(),
                wakeMinutes = readNullableInt(),
                name = readStringValue()
            )
        }
    }

    private fun DataInputStream.readCollectionSize(
        name: String
    ): Int {
        val count = readInt()

        require(count in 0..MAX_CHILD_RECORDS) {
            "Некорректное число $name."
        }

        return count
    }

    private fun DataOutputStream.writeStringValue(
        value: String
    ) {
        val bytes = value.toByteArray(
            StandardCharsets.UTF_8
        )

        require(bytes.size <= MAX_STRING_BYTES) {
            "Строка снимка слишком велика."
        }

        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readStringValue():
        String {
        val length = readInt()

        require(length in 0..MAX_STRING_BYTES) {
            "Некорректная длина строки."
        }

        val bytes = ByteArray(length)
        readFully(bytes)

        return String(
            bytes,
            StandardCharsets.UTF_8
        )
    }

    private fun DataOutputStream.writeNullableString(
        value: String?
    ) {
        writeBoolean(value != null)

        if (value != null) {
            writeStringValue(value)
        }
    }

    private fun DataInputStream.readNullableString():
        String? {
        return if (readBoolean()) {
            readStringValue()
        } else {
            null
        }
    }

    private fun DataOutputStream.writeNullableInt(
        value: Int?
    ) {
        writeBoolean(value != null)

        if (value != null) {
            writeInt(value)
        }
    }

    private fun DataInputStream.readNullableInt():
        Int? {
        return if (readBoolean()) {
            readInt()
        } else {
            null
        }
    }
}
