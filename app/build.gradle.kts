import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.JETBRAINS)
  }
  jvm()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core"))
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.material3)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.composeSyntaxHighlight.core)
      implementation(libs.composeSyntaxHighlight.kotlin)
      implementation(libs.composeSyntaxHighlight.ruby)
      implementation(libs.composeSyntaxHighlight.go)
      implementation(libs.composeSyntaxHighlight.rust)
      implementation(libs.composeSyntaxHighlight.swift)
      implementation(libs.composeSyntaxHighlight.python)
      implementation(libs.composeSyntaxHighlight.java)
      implementation(libs.composeSyntaxHighlight.javascript)
      implementation(libs.composeSyntaxHighlight.typescript)
      implementation(libs.composeSyntaxHighlight.markdown)
    }
    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutines.swing)
      implementation(libs.jbr.api)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
    jvmTest.dependencies {
      implementation(libs.compose.uiTest)
      implementation(compose.desktop.currentOs)
    }
  }
}

compose.desktop {
  application {
    mainClass = "io.github.mataku.kdiffviewer.app.MainKt"
    javaHome = javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(25))
      vendor.set(JvmVendorSpec.JETBRAINS)
    }.get().metadata.installationPath.asFile.absolutePath

    nativeDistributions {
      targetFormats(TargetFormat.Dmg)
      packageName = "kDiffViewer"
      packageVersion = "1.0.0"
      modules("java.instrument", "jdk.unsupported")

      macOS {
        bundleID = "io.github.mataku.kdiffviewer"
      }
    }
  }
}
