import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinSerialization)
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
      implementation(project(":protocol"))
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.material3)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
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
      implementation(libs.jetbrains.markdown)
      implementation(compose.components.resources)
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

compose.resources {
  packageOfResClass = "io.github.mataku.kdiffviewer.app.generated.resources"
}

val prepareKdvResources by tasks.registering(Sync::class) {
  dependsOn(":cli:linkReleaseExecutableMacosArm64")
  from(rootProject.layout.projectDirectory.file("cli/build/bin/macosArm64/releaseExecutable/kdv.kexe")) {
    rename { "kdv" }
  }
  into(layout.buildDirectory.dir("kdv-resources/macos-arm64"))
  filePermissions { unix("rwxr-xr-x") }
}

compose.desktop {
  application {
    mainClass = "io.github.mataku.kdiffviewer.app.MainKt"
    javaHome = javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(25))
      vendor.set(JvmVendorSpec.JETBRAINS)
    }.get().metadata.installationPath.asFile.absolutePath

    if (System.getProperty("os.name").startsWith("Mac")) {
      jvmArgs("-Xdock:name=kDiffViewer")
    }

    nativeDistributions {
      targetFormats(TargetFormat.Dmg)
      packageName = "kDiffViewer"
      packageVersion = "1.0.0"
      modules("java.instrument", "jdk.unsupported")
      appResourcesRootDir.set(layout.buildDirectory.dir("kdv-resources"))

      macOS {
        bundleID = "io.github.mataku.kdiffviewer"
      }
    }
  }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
  dependsOn(prepareKdvResources)
}

tasks.matching { it.name == "createDistributable" }.configureEach {
  val kdvBinary = layout.buildDirectory.file("compose/binaries/main/app/kDiffViewer.app/Contents/app/resources/kdv")
  doLast {
    kdvBinary.get().asFile.setExecutable(true, false)
  }
}
