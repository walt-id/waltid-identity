import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import groovy.json.JsonSlurper

plugins {
    id("com.github.jk1.dependency-license-report")
}
// Run: ./gradlew -p waltid-identity aggregateDependencyNotices --no-configuration-cache
configure<com.github.jk1.license.LicenseReportExtension> {
    // Keep each report scoped to the current project to avoid cross-project resolution locks.
    projects = arrayOf(project)

    // Write JSON so aggregateDependencyNotices can build THIRD-PARTY-NOTICE.md files.
    outputDir = layout.buildDirectory.dir("licenses").get().asFile.path
    renderers = arrayOf<ReportRenderer>(
        SimpleHtmlReportRenderer("THIRD-PARTY-NOTICE.html"),
        JsonReportRenderer("THIRD-PARTY-NOTICE.json")
    )
    filters = arrayOf<DependencyFilter>(LicenseBundleNormalizer())
}

// Ensure configurations exist (KMP creates them later), then pick runtime classpaths when possible.
project.afterEvaluate {
    val runtimePriority = listOf(
        "jvmMainRuntimeClasspath",
        "runtimeClasspath",
        "jvmRuntimeClasspath",
        "releaseRuntimeClasspath",
        "metadataRuntimeClasspath"
    )
    val runtimeConfigs = runtimePriority
        .mapNotNull { name -> project.configurations.findByName(name)?.takeIf { it.isCanBeResolved }?.name }
        .toMutableList()
    val additionalRuntimeConfigs = project.configurations
        .filter { it.isCanBeResolved && it.name.endsWith("RuntimeClasspath") && !it.name.contains("Test", ignoreCase = true) }
        .map { it.name }
        .filterNot { runtimeConfigs.contains(it) }
        .sorted()
    runtimeConfigs.addAll(additionalRuntimeConfigs)

    val selectedConfigs = if (runtimeConfigs.isNotEmpty()) {
        runtimeConfigs
    } else {
        val compilePriority = listOf(
            "jvmMainCompileClasspath",
            "compileClasspath",
            "jvmCompileClasspath",
            "metadataCompileClasspath",
            "commonMainCompileClasspath"
        )
        compilePriority
            .mapNotNull { name -> project.configurations.findByName(name)?.takeIf { it.isCanBeResolved }?.name }
    }

    if (selectedConfigs.isNotEmpty()) {
        project.extensions.configure<com.github.jk1.license.LicenseReportExtension> {
            configurations = selectedConfigs.toTypedArray()
        }
    }
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

// Aggregate all collected notices into one directory for easy consolidation

data class ThirdPartyInfo(val name: String, val version: String, val url: String, val license: String)

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

        fun isInternalDependency(moduleName: String): Boolean =
            moduleName.startsWith("id.walt.") || moduleName.startsWith("id.walt:")

        fun parseDepsFromJson(jsonFile: File): List<ThirdPartyInfo> {
            if (!jsonFile.exists()) return emptyList()
            val parsed = JsonSlurper().parse(jsonFile) as? Map<*, *> ?: return emptyList()
            val depList = parsed["dependencies"] as? List<*> ?: return emptyList()
            return depList.mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val name = map["moduleName"] as? String ?: return@mapNotNull null
                if (isInternalDependency(name)) return@mapNotNull null
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
                if (deps.isEmpty()) {
                    appendLine("* No third-party dependencies detected.")
                } else {
                    uniqueDeps(deps).forEach { dep ->
                        val linkTitle = if (dep.url.isNotBlank()) "[${dep.name} ${dep.version}](${dep.url})" else "${dep.name} ${dep.version}".trim()
                        appendLine("* $linkTitle. ${dep.license}.")
                        val noticeKey = "${dep.name.replace(":", ".")}-${dep.version}"
                        notices[noticeKey].orEmpty().forEach { noticeLine ->
                            appendLine("  - $noticeLine")
                        }
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
