plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Configuration-cache-friendly git commit count for automatic versioning.
val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.get()

val appVersionName = "2.$gitCommitCount"

/**
 * Release signing credentials, in order: environment variables (CI), then
 * the git-ignored .env file (local). Anything missing means the release
 * build keeps using the debug key — installable, but not publishable.
 */
val signingEnv: Map<String, String> = run {
    val fromFile = rootProject.file(".env").takeIf { it.exists() }
        ?.readLines()
        .orEmpty()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val key = trimmed.substringBefore('=').trim()
            val value = trimmed.substringAfter('=', "").trim()
            if (key.isEmpty() || value.isEmpty()) null else key to value
        }
        .toMap()
    val keys = listOf(
        "KOSHT_KEYSTORE_FILE",
        "KOSHT_KEY_ALIAS",
        "KOSHT_STORE_PASSWORD",
        "KOSHT_KEY_PASSWORD"
    )
    keys.mapNotNull { key ->
        val value = providers.environmentVariable(key).orNull ?: fromFile[key]
        value?.takeIf { it.isNotBlank() }?.let { key to it }
    }.toMap()
}

val releaseKeystore = signingEnv["KOSHT_KEYSTORE_FILE"]
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

val hasReleaseSigning = releaseKeystore != null && signingEnv.keys.containsAll(
    listOf("KOSHT_KEY_ALIAS", "KOSHT_STORE_PASSWORD", "KOSHT_KEY_PASSWORD")
)

android {
    namespace = "by.mlastovsky.kosht"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "by.mlastovsky.kosht"
        minSdk = 26
        targetSdk = 37
        // Version grows automatically with every commit/push.
        versionCode = gitCommitCount
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // APK artifacts are named kosht-<version>-<variant>.apk
    base.archivesName.set("kosht-$appVersionName")

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingEnv["KOSHT_STORE_PASSWORD"]
                keyAlias = signingEnv["KOSHT_KEY_ALIAS"]
                keyPassword = signingEnv["KOSHT_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Store builds use the real key from .env / CI secrets; without
            // it the debug key keeps personal installs working.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tesseract4android)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
