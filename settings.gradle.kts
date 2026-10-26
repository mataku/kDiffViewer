rootProject.name = "kDiffViewer"

pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") {
      content { includeModule("com.jetbrains", "jbr-api") }
    }
  }
}

include(":core")
include(":protocol")
include(":app")
include(":cli")
