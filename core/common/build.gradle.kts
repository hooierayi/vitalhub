plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.smarthealth.vitalhub.core.common"
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

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.material)
    api(libs.arouter.api)
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.fragment.ktx)
    debugImplementation(libs.compose.ui.tooling)
}
