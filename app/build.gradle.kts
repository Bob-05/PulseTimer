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
        applicationId = "com.example.pulsetimer"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}