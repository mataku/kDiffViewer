plugins {
  `kotlin-dsl`
}

dependencies {
  // Root build.gradle.kts loads the Kotlin plugin once (apply false), so every project shares its classes.
  compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
  plugins {
    register("kmpJvm") {
      id = "kdv.kmp-jvm"
      implementationClass = "io.github.mataku.kdiffviewer.buildlogic.KmpJvmConventionPlugin"
    }
    register("kmpNative") {
      id = "kdv.kmp-native"
      implementationClass = "io.github.mataku.kdiffviewer.buildlogic.KmpNativeConventionPlugin"
    }
  }
}
