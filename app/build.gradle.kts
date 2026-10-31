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
      implementation(libs.composeSyntaxHighlight.xml)
      implementation(libs.composeSyntaxHighlight.toml)
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

composeCompiler {
  stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

compose.resources {
  packageOfResClass = "io.github.mataku.kdiffviewer.app.generated.resources"
}

val isMacHost = System.getProperty("os.name").startsWith("Mac")
val isArmHost = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }

// The packaged app bundles kdv for the host it is built on. Compose copies kdv-resources/<os>-<arch> into the app's
// resources; Intel Macs are not supported.
val kdvTarget = when {
  isMacHost -> "macosArm64"
  isArmHost -> "linuxArm64"
  else -> "linuxX64"
}
val kdvResourceDir = when (kdvTarget) {
  "macosArm64" -> "macos-arm64"
  "linuxArm64" -> "linux-arm64"
  else -> "linux-x64"
}

val prepareKdvResources by tasks.registering(Sync::class) {
  dependsOn(":cli:linkReleaseExecutable${kdvTarget.replaceFirstChar { it.uppercase() }}")
  from(rootProject.layout.projectDirectory.file("cli/build/bin/$kdvTarget/releaseExecutable/kdv.kexe")) {
    rename { "kdv" }
  }
  into(layout.buildDirectory.dir("kdv-resources/$kdvResourceDir"))
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
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    if (isMacHost) {
      jvmArgs("-Xdock:name=kDiffViewer")
    }

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
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

      linux {
        packageName = "kdiffviewer"
        iconFile.set(project.file("packaging/kDiffViewer.png"))
        appCategory = "Development"
        menuGroup = "Development"
        shortcut = true
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val macAppContentResources = layout.projectDirectory.dir("packaging/app-content/Resources")

if (isMacHost) {
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

  val runBundleLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.JETBRAINS)
  }.zip(runBundle.map { it.file("Contents/MacOS/kDiffViewer") }, ::RunBundleJavaLauncher)

  afterEvaluate {
    tasks.named<JavaExec>("run") {
      dependsOn(prepareRunBundle)
      javaLauncher.set(runBundleLauncher)
      setExecutable(runBundleExecutable.get())
    }
  }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
  dependsOn(prepareKdvResources)
}

tasks.matching { it.name == "createDistributable" }.configureEach {
  val bundledKdv = if (isMacHost) "kDiffViewer.app/Contents/app/resources/kdv" else "kDiffViewer/lib/app/resources/kdv"
  val kdvBinary = layout.buildDirectory.file("compose/binaries/main/app/$bundledKdv")
  doLast {
    kdvBinary.get().asFile.setExecutable(true, false)
  }
}

// Compose copies app resources without their mode bits and always passes its own --resource-dir to jpackage, so the
// .deb is repacked: kdv becomes executable again, and the maintainer scripts link /usr/bin/kdv to it on install and
// remove the link on removal, leaving a /usr/bin/kdv that points elsewhere untouched.
tasks.matching { it.name == "packageDeb" || it.name == "packageReleaseDeb" }.configureEach {
  val outputDir = layout.buildDirectory.dir("compose/binaries/main${if (name == "packageReleaseDeb") "-release" else ""}/deb")
  val workDir = layout.buildDirectory.dir("compose/tmp/$name-repack")
  doLast {
    val bundledKdv = "/opt/kdiffviewer/lib/app/resources/kdv"
    fun exec(vararg command: String) {
      val process = ProcessBuilder(*command).inheritIO().start()
      check(process.waitFor() == 0) { "${command.joinToString(" ")} failed" }
    }
    val deb = outputDir.get().asFile.listFiles { file -> file.extension == "deb" }!!.single()
    val root = workDir.get().asFile
    root.deleteRecursively()
    exec("dpkg-deb", "-R", deb.absolutePath, root.absolutePath)
    root.resolve(bundledKdv.removePrefix("/")).setExecutable(true, false)
    fun insertAfter(script: String, anchor: String, line: String) {
      val file = root.resolve("DEBIAN/$script")
      val text = file.readText()
      check(anchor in text) { "$script has no '${anchor.trim()}' case" }
      file.writeText(text.replaceFirst(anchor, "$anchor$line\n"))
    }
    val ownsLink = "[ \"$(readlink /usr/bin/kdv)\" = $bundledKdv ]"
    insertAfter("postinst", "    configure)\n", "if [ ! -e /usr/bin/kdv ] || $ownsLink; then ln -sf $bundledKdv /usr/bin/kdv; fi")
    insertAfter("prerm", "    remove|upgrade|deconfigure)\n", "if $ownsLink; then rm -f /usr/bin/kdv; fi")
    exec("dpkg-deb", "--root-owner-group", "--build", root.absolutePath, deb.absolutePath)
    root.deleteRecursively()
  }
}

class RunBundleJavaLauncher(
  private val toolchain: JavaLauncher,
  private val executable: RegularFile,
) : JavaLauncher {
  override fun getMetadata(): JavaInstallationMetadata = toolchain.metadata

  override fun getExecutablePath(): RegularFile = executable
}
