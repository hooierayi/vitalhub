plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val analysisBaseUrl = providers.gradleProperty("analysisBaseUrl")
    .orElse("https://app.friendshipoffice.xyz/")
val analysisDebugBaseUrl = providers.gradleProperty("analysisDebugBaseUrl")
    .orElse("http://47.98.175.38:8000/")
val analysisApiKey = providers.gradleProperty("analysisApiKey").orElse("")
val analysisAppVersion = providers.gradleProperty("versionName").orElse("1.0")

android {
    namespace = "com.smarthealth.vitalhub.feature.analysis"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
        buildConfigField("String", "ANALYSIS_API_KEY", buildConfigString(analysisApiKey.get()))
        buildConfigField("String", "ANALYSIS_APP_VERSION", buildConfigString(analysisAppVersion.get()))
    }
    buildTypes {
        debug {
            buildConfigField(
                "String",
                "ANALYSIS_BASE_URL",
                buildConfigString(analysisDebugBaseUrl.get()),
            )
        }
        release {
            buildConfigField(
                "String",
                "ANALYSIS_BASE_URL",
                buildConfigString(analysisBaseUrl.get()),
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
}

kapt {
    arguments { arg("AROUTER_MODULE_NAME", project.name) }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:navi"))
    implementation(project(":core:network"))
    implementation(project(":foundation:bluetooth"))
    implementation(project(":provider:collection"))
    implementation(project(":provider:device"))
    implementation(project(":provider:record"))
    implementation(project(":provider:user"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.arouter.api)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.latex)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.html)
    implementation(libs.markwon.image.coil)
    implementation(libs.markwon.inline.parser)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.syntax.highlight) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.prism4j) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    kapt(libs.arouter.compiler)
    kapt(libs.prism4j.bundler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
