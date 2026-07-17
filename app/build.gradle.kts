import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
}

// Подпись релиза берётся из keystore.properties (НЕ в git). Пока файла нет — release подписывается
// debug-ключом (для смоук-проверки minified-сборки). Формат keystore.properties:
//   storeFile=/абсолютный/путь/release.keystore
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
val keystoreProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) FileInputStream(f).use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
  namespace = "ru.titeha.shiftalarm"
  compileSdk = 36

  defaultConfig {
    applicationId = "ru.titeha.shiftalarm"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures { compose = true }

  // Эталонные JSON-схемы Room доступны инструментальному тесту миграции как assets.
  sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

  signingConfigs {
    if (hasReleaseKeystore) {
      create("release") {
        storeFile = file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (hasReleaseKeystore) {
        signingConfigs.getByName("release")
      } else {
        // Временно: без keystore.properties подписываем debug-ключом — только для смоук-теста R8.
        signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

// Room экспортирует JSON-схему каждой версии БД — нужна для миграций и теста миграции.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Наш опубликованный компонент с JitPack
  implementation(libs.analog.timepicker)

  // Хранение настроек будильника
  implementation(libs.androidx.datastore.preferences)

  // Список будильников в БД
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  testImplementation(libs.junit)

  // Инструментальные тесты (на устройстве): тест миграции БД через MigrationTestHelper.
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.room.testing)
}
