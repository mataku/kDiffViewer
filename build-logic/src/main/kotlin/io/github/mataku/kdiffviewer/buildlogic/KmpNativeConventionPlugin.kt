package io.github.mataku.kdiffviewer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpNativeConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    target.extensions.configure<KotlinMultiplatformExtension> {
      macosArm64()
      linuxX64()
      linuxArm64()
    }
  }
}
