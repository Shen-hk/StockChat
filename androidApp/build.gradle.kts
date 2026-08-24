import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val deepSeekApiKey = localProperties.getProperty("DEEPSEEK_API_KEY", "").trim()

android {
    namespace = "com.kuikly.stockchat"
    compileSdk = 34
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.kuikly.stockchat"
        minSdk = 23
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DEEPSEEK_API_KEY", deepSeekApiKey.asBuildConfigString())
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.appcompat:appcompat:1.3.1")

    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")
}
