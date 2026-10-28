import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.nio.file.Files
import java.nio.file.Path

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
      implementation(libs.composeSyntaxHighlight.json)
      implementation(libs.composeSyntaxHighlight.yaml)
      implementation(libs.composeSyntaxHighlight.html)
      implementation(libs.composeSyntaxHighlight.css)
      implementation(libs.composeSyntaxHighlight.bash)
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

val jbrHome = javaToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(25))
  vendor.set(JvmVendorSpec.JETBRAINS)
}.get().metadata.installationPath.asFile.absolutePath

val appVersion = "1.0.0"
val appBuild = "1"
val appCopyright = "Copyright © 2026 Takuma Homma"

compose.desktop {
  application {
    mainClass = "io.github.mataku.kdiffviewer.app.MainKt"
    javaHome = jbrHome

    if (System.getProperty("os.name").startsWith("Mac")) {
      jvmArgs("-Xdock:name=kDiffViewer")
    }

    nativeDistributions {
      targetFormats(TargetFormat.Dmg)
      packageName = "kDiffViewer"
      packageVersion = appVersion
      copyright = appCopyright
      modules("java.instrument", "jdk.unsupported")
      appResourcesRootDir.set(layout.buildDirectory.dir("kdv-resources"))

      macOS {
        bundleID = "io.github.mataku.kdiffviewer"
        packageBuildVersion = appBuild
        iconFile.set(project.file("packaging/kDiffViewer.icns"))
        infoPlist {
          extraKeysRawXml = """
            |  <key>CFBundleIconName</key>
            |  <string>kDiffViewer</string>
          """.trimMargin()
        }
      }
    }
  }
}

val macAppContentResources = layout.projectDirectory.dir("packaging/app-content/Resources")

if (System.getProperty("os.name").startsWith("Mac")) {
  tasks.withType<AbstractJPackageTask>().configureEach {
    inputs.dir(macAppContentResources)
    freeArgs.addAll("--app-content", macAppContentResources.asFile.absolutePath)
  }

  val runBundle = layout.buildDirectory.dir("run-bundle/kDiffViewer.app")
  val runBundleExecutable = runBundle.map { it.file("Contents/MacOS/kDiffViewer").asFile.absolutePath }
  val prepareRunBundle by tasks.registering {
    val javaExecutable = "$jbrHome/bin/java"
    inputs.property("javaExecutable", javaExecutable)
    val version = appVersion
    val build = appBuild
    val copyright = appCopyright
    inputs.property("appVersion", version)
    inputs.property("appBuild", build)
    inputs.property("appCopyright", copyright)
    val iconFile = project.file("packaging/kDiffViewer.icns")
    val contentResources = macAppContentResources.asFile
    inputs.file(iconFile)
    inputs.dir(contentResources)
    outputs.dir(runBundle)
    doLast {
      val contents = runBundle.get().dir("Contents").asFile
      contents.deleteRecursively()
      contents.resolve("MacOS").mkdirs()
      contentResources.copyRecursively(contents.resolve("Resources"))
      iconFile.copyTo(contents.resolve("Resources/kDiffViewer.icns"))
      contents.resolve("Info.plist").writeText(
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        |<plist version="1.0">
        |<dict>
        |  <key>CFBundleName</key>
        |  <string>kDiffViewer</string>
        |  <key>CFBundleExecutable</key>
        |  <string>kDiffViewer</string>
        |  <key>CFBundleIdentifier</key>
        |  <string>io.github.mataku.kdiffviewer.dev</string>
        |  <key>CFBundlePackageType</key>
        |  <string>APPL</string>
        |  <key>CFBundleIconFile</key>
        |  <string>kDiffViewer.icns</string>
        |  <key>CFBundleIconName</key>
        |  <string>kDiffViewer</string>
        |  <key>CFBundleShortVersionString</key>
        |  <string>$version</string>
        |  <key>CFBundleVersion</key>
        |  <string>$build</string>
        |  <key>NSHumanReadableCopyright</key>
        |  <string>$copyright</string>
        |</dict>
        |</plist>
        |
        """.trimMargin(),
      )
      Files.createSymbolicLink(contents.resolve("MacOS/kDiffViewer").toPath(), Path.of(javaExecutable))
    }
  }

  afterEvaluate {
    tasks.named<JavaExec>("run") {
      dependsOn(prepareRunBundle)
      javaLauncher.set(null as JavaLauncher?)
      setExecutable(runBundleExecutable.get())
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
