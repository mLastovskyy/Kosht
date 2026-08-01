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
val dotEnv: Map<String, String> = rootProject.file(".env").takeIf { it.exists() }
    ?.readLines()
    .orEmpty()
    .mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
        val key = trimmed.substringBefore('=').trim()
        // Values may be quoted, as they are when copied out of a dashboard.
        val value = trimmed.substringAfter('=', "").trim().trim('"')
        if (key.isEmpty() || value.isEmpty()) null else key to value
    }
    .toMap()

/** Environment first (CI), then the git-ignored .env, then the fallback. */
fun config(key: String, fallback: String = ""): String =
    providers.environmentVariable(key).orNull?.takeIf { it.isNotBlank() }
        ?: dotEnv[key]?.takeIf { it.isNotBlank() }
        ?: fallback

val signingEnv: Map<String, String> = listOf(
    "KOSHT_KEYSTORE_FILE",
    "KOSHT_KEY_ALIAS",
    "KOSHT_STORE_PASSWORD",
    "KOSHT_KEY_PASSWORD"
).mapNotNull { key -> config(key).takeIf { it.isNotBlank() }?.let { key to it } }.toMap()

/**
 * Supabase client credentials. Only the project URL and the anon key ever
 * reach the APK: the anon key is meant to be public and carries no rights of
 * its own — row level security on `sync_rows` is what protects the data. The
 * service-role key and the database password stay in .env, out of the build.
 */
/**
 * AdMob identifiers. The fallbacks are Google's own test IDs: they serve test
 * ads to any build, so a checkout without .env never touches the real account
 * (and never risks an invalid-traffic strike). Put the live IDs in .env or CI
 * secrets as ADMOB_APP_ID and ADMOB_BANNER_UNIT_ID before publishing.
 */
val admobAppId = config("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
val admobBannerUnitId = config("ADMOB_BANNER_UNIT_ID", "ca-app-pub-3940256099942544/9214589741")

val supabaseUrl = config("SUPABASE_URL", "https://sqwueufwjgunbarfbnpx.supabase.co")
val supabaseAnonKey = config(
    "SUPABASE_ANON_KEY",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNxd3VldWZ3" +
        "amd1bmJhcmZibnB4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUwMDA1MjksImV4cCI6MjEwMDU3Nj" +
        "UyOX0.-7e_Daj_FDJoZLoOptZLX2U3y85IhLRK3Ko-pK79okg"
)

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

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBannerUnitId\"")

        manifestPlaceholders["admobAppId"] = admobAppId
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
        buildConfig = true
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
    // Fingerprint/face unlock through one API across every version we support.
    implementation(libs.androidx.biometric)
    implementation(libs.tesseract4android)
    implementation(libs.zxing.core)
    implementation(libs.androidx.work.runtime)
    implementation(libs.play.services.ads)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
