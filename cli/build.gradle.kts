plugins {
  alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
  macosArm64 {
    binaries.executable {
      baseName = "kdv"
      entryPoint = "io.github.mataku.kdiffviewer.cli.main"
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
