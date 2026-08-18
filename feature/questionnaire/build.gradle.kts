plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
}

android {
    namespace = "com.smarthealth.vitalhub.feature.questionnaire"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
}

kapt {
    arguments { arg("AROUTER_MODULE_NAME", project.name) }
}

dependencies {
    implementation(project(":core:common"))
    implementation(platform(libs.compose.bom))
    kapt(libs.arouter.compiler)
    testImplementation(libs.junit)
}
