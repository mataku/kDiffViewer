plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.spotless)
}

val ktlintOverrides = mapOf(
  "ktlint_standard_filename" to "disabled",
  "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
)

spotless {
  kotlin {
    target("core/src/**/*.kt", "app/src/**/*.kt")
    targetExclude("**/build/**")
    ktlint().editorConfigOverride(ktlintOverrides)
  }
  kotlinGradle {
    target("*.gradle.kts", "core/*.gradle.kts", "app/*.gradle.kts")
    ktlint().editorConfigOverride(ktlintOverrides)
  }
}
