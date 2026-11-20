rootProject.name = "build-logic"

pluginManagement {
  repositories {
    exclusiveContent {
      forRepository { gradlePluginPortal() }
      filter { includeGroupAndSubgroups("org.gradle.kotlin") }
    }
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
