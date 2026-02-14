plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.android.wm.shell.shared"

    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src"))
            manifest.srcFile("AndroidManifest.xml")
            res.setSrcDirs(listOf("res"))
        }
    }
    kotlin {
        jvmToolchain(21)//1.8（8）
    }
}

dependencies {
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core-animation:1.0.0")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.1.0")
    implementation("javax.inject:javax.inject:1")
    implementation(project(":flags"))
    compileOnly(files(rootProject.file("prebuilts/libs/framework-16.jar")))
    compileOnly(files(rootProject.file("prebuilts/libs/WindowManager-Shell-shared.jar")))
    compileOnly(files(rootProject.file("prebuilts/libs/WindowManager-Shell-shared_kotlin.jar")))
    compileOnly(files(rootProject.file("prebuilts/libs/SystemUI-statsd.jar")))
    compileOnly(files(rootProject.file("prebuilts/libs/com_android_window_flags.jar")))
    compileOnly(files(rootProject.file("prebuilts/libs/com_android_wm_shell_flags.jar")))
}
