plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "com.chispa.local"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.chispa.local"
    minSdk = 33
    targetSdk = 35
    versionCode = 3
    versionName = "0.3.0"
    ndk { abiFilters += "arm64-v8a" }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}
dependencies {
  implementation(project(":llamalib"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
