package ru.titeha.shiftalarm.alarm

/**
 * Инструкция по настройке телефона под конкретного производителя, чтобы будильник переживал
 * перезагрузку и не выгружался «менеджерами» фона.
 *
 * Многие прошивки (особенно Xiaomi/MIUI, Huawei, Oppo/Vivo) по умолчанию ЗАПРЕЩАЮТ автозапуск и
 * агрессивно чистят фон — тогда после перезагрузки будильники не перевыставляются вообще (проверено
 * на устройстве: с выключенным автозапуском звонок не встаёт после ребута). Системе это не сообщить
 * программно, поэтому проводим пользователя за руку.
 *
 * @param autostartComponents кандидаты `пакет/класс` экрана автозапуска — пробуются best-effort с
 *   откатом на детали приложения (имена зависят от версии прошивки).
 */
data class VendorGuide(
    val vendorName: String,
    val steps: List<String>,
    val autostartComponents: List<String>,
)

object VendorSetup {

    const val DONT_KILL_MY_APP_URL = "https://dontkillmyapp.com"

    /** Инструкция для [manufacturer] ([android.os.Build.MANUFACTURER]) или null — прошивка не из агрессивных. */
    fun forManufacturer(manufacturer: String?): VendorGuide? {
        val m = manufacturer?.lowercase()?.trim().orEmpty()

        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
                VendorGuide(
                    vendorName = "Xiaomi (MIUI/HyperOS)",
                    steps = listOf(
                        "Включите «Автозапуск» для приложения (Безопасность → Разрешения → Автозапуск).",
                        "Снимите ограничения энергосбережения: «Без ограничений» (Настройки → Приложения → ShiftAlarm → Экономия батареи).",
                        "Заблокируйте приложение в списке недавних (потяните карточку вниз → замок), чтобы система его не выгружала."
                    ),
                    autostartComponents = listOf(
                        "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )

            m.contains("huawei") || m.contains("honor") ->
                VendorGuide(
                    vendorName = "Huawei / Honor",
                    steps = listOf(
                        "Разрешите автозапуск (Настройки → Приложения → ShiftAlarm → Запуск: включите ручное управление, все три пункта ON).",
                        "Снимите ограничения энергосбережения для приложения.",
                        "Заблокируйте приложение в недавних, чтобы оно не выгружалось."
                    ),
                    autostartComponents = listOf(
                        "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",
                        "com.huawei.systemmanager/.optimize.process.ProtectActivity"
                    )
                )

            m.contains("oppo") || m.contains("realme") ->
                VendorGuide(
                    vendorName = "Oppo / Realme (ColorOS)",
                    steps = listOf(
                        "Разрешите автозапуск для приложения (Настройки → Управление приложениями → ShiftAlarm → Автозапуск).",
                        "Снимите ограничения энергосбережения (разрешите фоновую работу).",
                        "Заблокируйте приложение в недавних."
                    ),
                    autostartComponents = listOf(
                        "com.coloros.safecenter/.permission.startup.StartupAppListActivity",
                        "com.coloros.safecenter/.startupapp.StartupAppListActivity"
                    )
                )

            m.contains("vivo") || m.contains("iqoo") ->
                VendorGuide(
                    vendorName = "Vivo / iQOO",
                    steps = listOf(
                        "Разрешите автозапуск (i Manager → Управление приложениями → Автозапуск).",
                        "Разрешите фоновую работу и снимите ограничения батареи.",
                        "Заблокируйте приложение в недавних."
                    ),
                    autostartComponents = listOf(
                        "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity",
                        "com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager"
                    )
                )

            m.contains("samsung") ->
                VendorGuide(
                    vendorName = "Samsung",
                    steps = listOf(
                        "Отключите «Перевод в режим сна» для приложения (Настройки → Обслуживание устройства → Батарея → Ограничения фона).",
                        "Добавьте приложение в «Никогда не переводить в спящий режим».",
                        "Снимите ограничения энергосбережения."
                    ),
                    autostartComponents = emptyList()
                )

            else -> null
        }
    }
}
