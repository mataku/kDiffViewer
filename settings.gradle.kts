rootProject.name = "kDiffViewer"

pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenLocal {
      content { includeGroup("io.github.mataku") }
    }
    mavenCentral()
    google()
  }
}

include(":core")
include(":app")
