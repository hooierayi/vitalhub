plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.smarthealth.vitalhub.debug.dokit.protocol"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(project(":foundation:device-api"))
    implementation(libs.androidx.appcompat)
    implementation(libs.dokitx) {
        exclude(group = "com.android.volley", module = "volley")
    }
    implementation(libs.volley)
}
