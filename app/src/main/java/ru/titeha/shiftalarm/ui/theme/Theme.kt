package ru.titeha.shiftalarm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Выбор темы пользователем: по системе / всегда светлая / всегда тёмная. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
  primary = BluePrimaryLight,
  secondary = BlueSecondaryLight,
  tertiary = BlueTertiaryLight,
)

private val DarkColors = darkColorScheme(
  primary = BluePrimaryDark,
  secondary = BlueSecondaryDark,
  tertiary = BlueTertiaryDark,
)

/**
 * Тема приложения. Светлая/тёмная выбирается по системной настройке. На Android 12+ по умолчанию
 * используются динамические цвета Material You (адаптируются под обои — нативный, «дорогой» вид);
 * на старых версиях — фирменная синяя палитра. Единая точка входа темы: оборачивать здесь весь UI,
 * чтобы дальнейший полиш шёл поверх, без перекраски.
 */
@Composable
fun AppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  fontScale: Float = 1f,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColors
    else -> LightColors
  }
  MaterialTheme(colorScheme = colorScheme) {
    // Пользовательский масштаб шрифта ПОВЕРХ системного (для слабовидящих): множим системный fontScale.
    val base = LocalDensity.current
    CompositionLocalProvider(
      LocalDensity provides Density(density = base.density, fontScale = base.fontScale * fontScale)
    ) {
      content()
    }
  }
}
