plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.smarthealth.vitalhub.foundation.device.sdk"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(project(":foundation:bluetooth"))
    implementation(project(":foundation:device-api"))
    implementation(project(":foundation:device-transport"))
    implementation(project(":foundation:device-protocol"))
    implementation(project(":foundation:device-command"))
    implementation(project(":foundation:device-storage"))
    implementation(project(":foundation:device-waveform"))
    implementation(libs.kotlinx.coroutines.core)
}
