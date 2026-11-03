package io.github.mataku.kdiffviewer.buildlogic

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmVendorSpec

fun JavaToolchainSpec.useJbr() {
  languageVersion.set(JavaLanguageVersion.of(25))
  vendor.set(JvmVendorSpec.JETBRAINS)
}
