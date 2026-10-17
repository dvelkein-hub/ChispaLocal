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
    versionCode = 2
    versionName = "0.2.0"
    ndk { abiFilters += "arm64-v8a" }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {
  implementation(project(":llamalib"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
