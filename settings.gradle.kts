rootProject.name = "kDiffViewer"

pluginManagement {
  includeBuild("build-logic")
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositories {
    exclusiveContent {
      forRepository { google() }
      filter { includeGroupAndSubgroups("androidx") }
    }
    exclusiveContent {
      forRepository { maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
      filter { includeModule("com.jetbrains", "jbr-api") }
    }
    mavenCentral()
  }
}

include(":core")
include(":protocol")
include(":app")
include(":cli")
