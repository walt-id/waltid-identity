import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import groovy.json.JsonSlurper
import org.gradle.api.tasks.TaskProvider

data class ThirdPartyInfo(val name: String, val version: String, val url: String, val license: String)

allprojects {
    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
        maven("https://jitpack.io")
    }
}

plugins {
    val kotlinVersion = "2.2.21"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.power-assert") version kotlinVersion apply false

    kotlin("plugin.compose") version kotlinVersion apply false

    kotlin("plugin.serialization") version kotlinVersion apply false

    id("love.forte.plugin.suspend-transform") version "2.2.20-0.13.1" apply false

    id("com.android.library") version "8.12.3" apply false
    id("com.android.application") version "8.12.3" apply false

    id("com.github.ben-manes.versions") version "0.53.0" apply false
    id("io.ktor.plugin") version "3.3.3" apply false

    id("com.github.jk1.dependency-license-report") version "3.0.1" apply false
}
dependencies {
    implementation(kotlin("stdlib"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(8)
}

allprojects {
    tasks.withType<DependencyUpdatesTask> {
        rejectVersionIf {
            listOf("-beta", "-alpha", "-rc").any { it in candidate.version.lowercase() } || candidate.version.takeLast(4).contains("RC")
        }
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "com.github.jk1.dependency-license-report")

        // configure the license report once per subproject
        configure<com.github.jk1.license.LicenseReportExtension> {
            // Collect from this project + all subprojects (typical for multi-module)
            projects = (listOf(project) + project.subprojects).toTypedArray()

            // Only runtime artifacts (what you really ship)
            configurations = arrayOf("runtimeClasspath")

            // Where to put reports
            outputDir = layout.buildDirectory.dir("licenses").get().asFile.path

            // Render as HTML + JSON so we can consolidate later
            renderers = arrayOf<ReportRenderer>(
                SimpleHtmlReportRenderer("THIRD-PARTY-NOTICE.html"),
                JsonReportRenderer("THIRD-PARTY-NOTICE.json")
            )

            // Optional but nice: normalize “Apache 2”, “Apache-2.0”, etc.
            filters = arrayOf<DependencyFilter>(
                LicenseBundleNormalizer()
            )
        }

        // Collect NOTICE/LICENSE files from all runtime dependencies so they can be merged into a root NOTICE later
        val collectDependencyNotices = tasks.register("collectDependencyNotices") {
            val noticeOutput = layout.buildDirectory.dir("licenses/notices")
            outputs.dir(noticeOutput)

            doLast {
                val outputDirFile = noticeOutput.get().asFile
                val artifacts = runCatching {
                    project.configurations.getByName("runtimeClasspath").resolvedConfiguration.resolvedArtifacts
                }.getOrElse {
                    logger.warn("Skipping notice collection for ${project.path}: ${it.message}")
                    return@doLast
                }

                artifacts.forEach { artifact ->
                    val id = artifact.moduleVersion.id
                    project.copy {
                        from(zipTree(artifact.file)) {
                            includeEmptyDirs = false
                            include("META-INF/NOTICE*", "NOTICE*", "META-INF/LICENSE*", "LICENSE*")
                        }
                        into(outputDirFile.resolve("${id.group}.${id.name}-${id.version}"))
                    }
                }
            }
        }

        tasks.named("generateLicenseReport") {
            finalizedBy(collectDependencyNotices)
        }
    }
}

// Aggregate all collected notices into one directory for easy consolidation
val aggregateDependencyNotices = tasks.register("aggregateDependencyNotices") {
    val aggregatedDir = layout.buildDirectory.dir("licenses/all-notices")
    outputs.dir(aggregatedDir)

    // Run all subproject notice collectors first (where available)
    val noticeTasks: List<TaskProvider<*>> = subprojects.mapNotNull { sub ->
        sub.tasks.findByName("collectDependencyNotices")?.let { sub.tasks.named("collectDependencyNotices") }
    }
    val licenseReportTasks: List<TaskProvider<*>> = subprojects.mapNotNull { sub ->
        sub.tasks.findByName("generateLicenseReport")?.let { sub.tasks.named("generateLicenseReport") }
    }
    noticeTasks.forEach { dependsOn(it) }
    licenseReportTasks.forEach { dependsOn(it) }

    doLast {
        val targetDir = aggregatedDir.get().asFile
        project.delete(targetDir)

        subprojects.forEach { sub ->
            val noticesDir = sub.layout.buildDirectory.dir("licenses/notices").get().asFile
            if (noticesDir.exists()) {
                project.copy {
                    from(noticesDir)
                    into(targetDir.resolve(sub.path.removePrefix(":").replace(":", "/")))
                }
            }
        }

        fun collectNotices(noticeRoot: File): Map<String, Set<String>> {
            val notices = mutableMapOf<String, MutableSet<String>>()
            if (!noticeRoot.exists()) return notices
            noticeRoot.walkTopDown()
                .filter { it.isFile && it.name.contains("NOTICE", ignoreCase = true) }
                .forEach { file ->
                    val dependencyKey = file.parentFile?.parentFile?.name ?: return@forEach
                    val copyrights = file.readLines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { it.trimStart('#', '-', '*', ' ').trim() }
                        .filter { it.startsWith("copyright", ignoreCase = true) }
                        .filter { line -> line.any { it.isDigit() } || line.contains("©") || line.length > 15 }
                    if (copyrights.isNotEmpty()) notices.getOrPut(dependencyKey) { linkedSetOf() }.addAll(copyrights)
                }
            return notices
        }

        fun parseDepsFromJson(jsonFile: File): List<ThirdPartyInfo> {
            if (!jsonFile.exists()) return emptyList()
            val parsed = JsonSlurper().parse(jsonFile) as? Map<*, *> ?: return emptyList()
            val depList = parsed["dependencies"] as? List<*> ?: return emptyList()
            return depList.mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val name = map["moduleName"] as? String ?: return@mapNotNull null
                val version = map["moduleVersion"] as? String ?: ""
                val url = map["moduleUrl"] as? String ?: ""
                val license = map["moduleLicense"] as? String ?: "License not specified"
                ThirdPartyInfo(name, version, url, license)
            }
        }

        fun uniqueDeps(deps: List<ThirdPartyInfo>): List<ThirdPartyInfo> =
            deps.groupBy { "${it.name}:${it.version}" }
                .values
                .map { entries -> entries.firstOrNull { it.url.isNotBlank() } ?: entries.first() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        fun renderThirdPartyMd(title: String, intro: String, deps: List<ThirdPartyInfo>, notices: Map<String, Set<String>>): String =
            buildString {
                appendLine("# $title")
                appendLine()
                appendLine(intro)
                appendLine()
                uniqueDeps(deps).forEach { dep ->
                    val linkTitle = if (dep.url.isNotBlank()) "[${dep.name} ${dep.version}](${dep.url})" else "${dep.name} ${dep.version}".trim()
                    appendLine("* $linkTitle. ${dep.license}.")
                    val noticeKey = "${dep.name.replace(":", ".")}-${dep.version}"
                    notices[noticeKey].orEmpty().forEach { noticeLine ->
                        appendLine("  - $noticeLine")
                    }
                }
            }

        // Root-level combined THIRD-PARTY-NOTICE.md
        val rootNotices = collectNotices(targetDir)
        val rootDeps = subprojects.flatMap { sub ->
            val jsonFile = sub.layout.buildDirectory.file("licenses/THIRD-PARTY-NOTICE.json").get().asFile
            parseDepsFromJson(jsonFile)
        }
        layout.projectDirectory.file("THIRD-PARTY-NOTICE.md").asFile.writeText(
            renderThirdPartyMd(
                title = "Third-Party Software",
                intro = "This document lists third-party libraries used by walt.id identity.",
                deps = rootDeps,
                notices = rootNotices
            )
        )

        // Per-project THIRD-PARTY-NOTICE.md files
        subprojects.forEach { sub ->
            val subDeps = parseDepsFromJson(sub.layout.buildDirectory.file("licenses/THIRD-PARTY-NOTICE.json").get().asFile)
            if (subDeps.isEmpty()) return@forEach
            val subNoticesDir = sub.layout.buildDirectory.dir("licenses/notices").get().asFile
            val subNotices = collectNotices(subNoticesDir)
            val content = renderThirdPartyMd(
                title = "Third-Party Software for ${sub.path}",
                intro = "This document lists third-party libraries used by ${sub.path}.",
                deps = subDeps,
                notices = subNotices
            )
            sub.projectDir.resolve("THIRD-PARTY-NOTICE.md").writeText(content)
        }
    }
}

// test
