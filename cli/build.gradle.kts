plugins {
  alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
  listOf(macosArm64(), linuxX64(), linuxArm64()).forEach { target ->
    target.binaries.executable {
      baseName = "kdv"
      entryPoint = "io.github.mataku.kdiffviewer.cli.main"
    }
    target.compilations.getByName("main").cinterops.create("spawn") {
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
