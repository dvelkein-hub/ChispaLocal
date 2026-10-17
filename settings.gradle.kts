pluginManagement {
  repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories { google(); mavenCentral() }
}
rootProject.name = "ChispaLocal"
include(":app")
include(":llamalib")
project(":llamalib").projectDir = file("third_party/llama.cpp/examples/llama.android/lib")
