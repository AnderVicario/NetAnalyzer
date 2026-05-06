import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

fun gitCommitDate(): String {
    return try {
        val process = ProcessBuilder("git", "log", "-1", "--format=%cd", "--date=iso")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText().trim() }
    } catch (e: Exception) {
        ""
    }
}

val commitDate = gitCommitDate()
// Parseamos la fecha ISO de Git → "2025-09-23 15:45:12 +0200"
val sdfInput = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
val sdfOutputDay = SimpleDateFormat("dd", Locale.US)
val sdfOutputHour = SimpleDateFormat("HH", Locale.US)
val sdfOutputMonth = SimpleDateFormat("MM", Locale.US)

val date = try {
    sdfInput.parse(commitDate)
} catch (e: Exception) {
    Date() // Usar Date() en lugar de null
}

val monthMap = mapOf(
    "09" to 7,
    "10" to 8,
    "11" to 9,
    "12" to 10,
    "01" to 11,
    "02" to 12,
    "03" to 1,
    "04" to 2,
    "05" to 3,
    "06" to 4,
    "07" to 5,
    "08" to 6
)

val monthNumber = monthMap[sdfOutputMonth.format(date)] ?: 0
val day = sdfOutputDay.format(date)
val hour = sdfOutputHour.format(date)

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { input ->
        localProperties.load(input)
    }
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.av19.netanalyzer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.av19.netanalyzer"
        minSdk = 24
        targetSdk = 36
        versionName = "$monthNumber.$day.$hour"
        // VersionCode derivado de fecha (ej: 25092316)
        versionCode = ("25${monthNumber.toString().padStart(2, '0')}$day$hour").toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.okhttp)
    implementation(libs.json)
    implementation(libs.gson)
    implementation(libs.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}