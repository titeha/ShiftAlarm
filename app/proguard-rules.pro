# Keep-правила R8 для release-сборки.

# --- Room ---
# Сущности/DAO/репозиторий читаются генерированным кодом Room; слой данных мал — держим целиком,
# чтобы R8 не переименовал/не выкинул поля сущностей.
-keep class ru.titeha.shiftalarm.data.** { *; }

# --- Enum по имени ---
# Настройки и кодеки пишут/читают enum через name()/valueOf() (тема, режим снуза, тип периода,
# StateDayKind, DirectBoot-кэш и т.п.). Дублирует правило из proguard-android-optimize, но надёжнее явно.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Компоненты Android (AlarmReceiver/BootReceiver/AlarmService/Activity) объявлены в манифесте и
# удерживаются Android Gradle Plugin автоматически — отдельных правил не требуется.
