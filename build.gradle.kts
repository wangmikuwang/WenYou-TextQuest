// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// 构建输出移出 OneDrive（其目录经常被云同步占用句柄导致 AccessDenied/删除失败）。
val sharedBuildRoot = file(System.getenv("WENYOU_BUILD_DIR")
    ?: "${System.getProperty("user.home")}/.gradle/caches/wnq-build/${rootProject.name}")
allprojects {
    layout.buildDirectory.set(File(sharedBuildRoot, project.name))
}
