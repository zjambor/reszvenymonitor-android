import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// A web-app config.js megfelelője: a gitignore-olt secrets.properties-ből
// BuildConfig-mezők készülnek. Hiányzó fájl/kulcs → azonnali, magyarázó build-hiba.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    require(file.exists()) {
        "Hiányzik az android/secrets.properties — másold le a secrets.properties.example sablont, és töltsd ki."
    }
    file.inputStream().use { load(it) }
}

fun secret(key: String): String =
    secrets.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: error("Hiányzó kulcs az android/secrets.properties-ben: $key")

// Kiadás-aláírás. A keystore a repón KÍVÜL él, a jelszavai a gitignore-olt
// android/keystore.properties-ben (sablon: keystore.properties.example).
// Ha a fájl hiányzik, a release build ALÁÍRATLAN APK-t készít — így a projekt
// idegen gépen, keystore nélkül is lefordul.
val keystoreProps: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
    namespace = "hu.jamborz.reszvenymonitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.jamborz.reszvenymonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "APP_USER", "\"${secret("APP_USER")}\"")
        buildConfigField("String", "APP_EMAIL", "\"${secret("APP_EMAIL")}\"")
        buildConfigField("String", "START_DATE", "\"${secret("START_DATE")}\"")
        buildConfigField("String", "DEFAULT_TICKER", "\"${secret("DEFAULT_TICKER")}\"")
        buildConfigField("String", "DEFAULT_DISPLAY_CURRENCY", "\"${secret("DEFAULT_DISPLAY_CURRENCY")}\"")
    }

    signingConfigs {
        keystoreProps?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    // Supabase (Auth + Postgrest defaultSchema="stocks" + Functions) — a 2. fázistól épül be
    // ténylegesen; a pinnelt verziók feloldhatóságát már az 1. fázis build igazolja.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)

    // Grafikon — az 5. fázis használja, a pin feloldhatósága itt igazolódik.
    implementation(libs.mpandroidchart)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
