plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.spotless)
}

val ktlintOverrides = mapOf(
  "ktlint_standard_no-unused-imports" to "enabled",
  "ktlint_standard_filename" to "disabled",
  "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
)

spotless {
  kotlin {
    target("core/src/**/*.kt", "app/src/**/*.kt", "protocol/src/**/*.kt", "cli/src/**/*.kt", "build-logic/src/**/*.kt")
    // No targetExclude("**/build/**"): Spotless resolves it by walking the whole project, including the module build directories that compile tasks are rewriting, and the task fails with "Could not read path".
    ktlint().editorConfigOverride(ktlintOverrides)
  }
  kotlinGradle {
    target("*.gradle.kts", "core/*.gradle.kts", "app/*.gradle.kts", "protocol/*.gradle.kts", "cli/*.gradle.kts", "build-logic/*.gradle.kts")
    ktlint().editorConfigOverride(ktlintOverrides)
  }
}
