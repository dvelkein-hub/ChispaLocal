plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
val signingReady = listOf("CHISPA_KEYSTORE_FILE", "CHISPA_STORE_PASSWORD",
 "CHISPA_KEY_ALIAS", "CHISPA_KEY_PASSWORD").all { !System.getenv(it).isNullOrBlank() }
android {
  signingConfigs {
    if (signingReady) {
      create("chispaStable") {
        storeFile = file(System.getenv("CHISPA_KEYSTORE_FILE"))
        storePassword = System.getenv("CHISPA_STORE_PASSWORD")
        keyAlias = System.getenv("CHISPA_KEY_ALIAS")
        keyPassword = System.getenv("CHISPA_KEY_PASSWORD")
      }
    }
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      if (signingReady) signingConfig = signingConfigs.getByName("chispaStable")
    }
  }
  namespace = "com.chispa.local"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.chispa.local"
    minSdk = 33
    targetSdk = 35
    versionCode = 5
    versionName = "0.5.0"
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
