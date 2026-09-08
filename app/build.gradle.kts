import java.io.FileOutputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 版本号从 version.properties 读取：每次改动执行 `gradlew bumpVersion` 即升一次版。
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionMajor: String = versionProps.getProperty("versionMajor", "1")
val appVersionMinor: String = versionProps.getProperty("versionMinor", "1")
val appVersionPatch: String = versionProps.getProperty("versionPatch", "0")
val appVersionCode: Int = versionProps.getProperty("versionCode", "1").toIntOrNull() ?: 1
val appVersionName: String = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

android {
    namespace = "io.wenyou.textquest"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.wenyou.textquest"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 两个可独立安装的版本：full（含内置 LGBT/多题材预设）/ bare（内置一套“非 LGBT”剧情与角色）
    flavorDimensions += "content"
    productFlavors {
        create("bare") {
            dimension = "content"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-bare"
            buildConfigField("boolean", "BUILTIN_CONTENT", "false")
            buildConfigField("boolean", "BARE_CONTENT", "true")
        }
        create("full") {
            dimension = "content"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
            buildConfigField("boolean", "BUILTIN_CONTENT", "true")
            buildConfigField("boolean", "BARE_CONTENT", "false")
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// 每次对软件做修改，视为一次升级：运行 `gradlew bumpVersion`（+patch / +versionCode），再 assemble。
tasks.register("bumpVersion") {
    doLast {
        val f = rootProject.file("version.properties")
        val p = Properties().apply { f.inputStream().use { load(it) } }
        val patch = (p.getProperty("versionPatch", "0").toIntOrNull() ?: 0) + 1
        val code = (p.getProperty("versionCode", "1").toIntOrNull() ?: 1) + 1
        p["versionPatch"] = patch.toString()
        p["versionCode"] = code.toString()
        FileOutputStream(f).use { p.store(it, "WenYou version; run 'gradlew bumpVersion' then assemble") }
        println("已升版 -> ${p["versionMajor"]}.${p["versionMinor"]}.$patch（versionCode=$code）")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
