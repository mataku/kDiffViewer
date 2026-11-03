import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  id("kdv.kmp-native")
}

kotlin {
  targets.withType<KotlinNativeTarget>().configureEach {
    binaries.executable {
      baseName = "kdv"
      entryPoint = "io.github.mataku.kdiffviewer.cli.main"
    }
    compilations.getByName("main").cinterops.create("spawn") {
      defFile(project.file("src/nativeInterop/cinterop/spawn.def"))
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":protocol"))
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
