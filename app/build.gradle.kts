import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Копируем юридические документы из корня репозитория в сгенерированный
// assets-каталог. В самом репозитории дубликаты не храним.
//
// Явно указываем тип Provider<Directory>: layout.buildDirectory.dir()
// возвращает платформенный тип, и без явной аннотации Kotlin выводит
// его как «тип с платформенной nullability» — это даёт weak warning в IDE.
val legalAssetsDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/legalAssets")

// Параметры подписи релиза читаем из keystore.properties в корне проекта.
// Файл не коммитится (см. .gitignore), поэтому секреты в репозиторий не попадают.
// Если файла нет, release собирается без подписи — его можно подписать вручную
// через Android Studio → Build → Generate Signed Bundle / APK.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val copyLegalDocs by tasks.registering(Copy::class) {
    from(rootProject.file("PRIVACY_POLICY.md")) { into("legal") }
    from(rootProject.file("TERMS_OF_USE.md")) { into("legal") }
    into(legalAssetsDir)
}

tasks.named("preBuild") { dependsOn(copyLegalDocs) }

android {
    namespace = "com.pulsetimer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.bahtiarazizov.pulsetimer"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Конфигурация создаётся только при наличии keystore.properties,
        // иначе release остаётся неподписанным — как было раньше.
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 отключён. Правила proguard намеренно не подключены,
            // чтобы не создавать иллюзию работающей обфускации.
            // При включении R8 — вернуть proguardFiles(...) и добавить
            // -keep правила для Room (см. proguard-rules.pro).
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Тесты контроллера обучения гоняют корутины подготовки на JVM: без этого
        // android.util.Log из них бросает «not mocked».
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        getByName("main") {
            // Каталог сгенерирован до сборки APK зависимостью preBuild → copyLegalDocs.
            // Добавляем File через directories, чтобы не использовать устаревший srcDir API.
            assets.directories.add(legalAssetsDir.get().asFile.absolutePath)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
}