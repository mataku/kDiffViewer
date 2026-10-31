plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.JETBRAINS)
  }
  jvm()
  macosArm64()
  linuxX64()
  linuxArm64()

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.serialization.json)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
