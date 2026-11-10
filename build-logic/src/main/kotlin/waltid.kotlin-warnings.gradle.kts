import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

// Apply to every Kotlin compilation, including tests and platform-specific source sets.
// Deprecation debt is migrated per module; missing experimental opt-ins must not accumulate.
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xwarning-level=OPT_IN_USAGE:error",
            "-Xwarning-level=OPT_IN_OVERRIDE:error",
        )
    }
}
