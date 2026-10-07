pluginManagement {
    // Settings plugins resolve before version catalog accessors are available.
    fun catalogVersion(alias: String): String {
        val assignment = Regex("""\s*${Regex.escape(alias)}\s*=\s*"([^"]+)"\s*(?:#.*)?""")
        val versions = file("../gradle/libs.versions.toml").readLines()
            .dropWhile { it.trim() != "[versions]" }
            .drop(1)
            .takeWhile { !it.trimStart().startsWith("[") }
            .mapNotNull { assignment.matchEntire(it)?.groupValues?.get(1) }
        return versions.singleOrNull()
            ?: error("Expected one quoted [versions].$alias entry in ../gradle/libs.versions.toml")
    }

    plugins {
        id("io.github.ben-manes.versions.settings") version catalogVersion("versions")
    }
}

plugins {
    id("io.github.ben-manes.versions.settings")
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("identityLibs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "build-logic"
