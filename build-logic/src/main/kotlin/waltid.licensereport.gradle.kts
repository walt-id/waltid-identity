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

    // Declaring the runtime classpath as an input makes Gradle build the first-party jars on it
    // before this task runs.
    inputs.files(project.files(provider { project.configurations.findByName("runtimeClasspath") ?: project.files() }))
        .withPropertyName("runtimeClasspath")
        .optional()

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

val dualLicenseElections: Map<String, String> = mapOf(
    // "LGPLv3 or Apache 2.0" — https://github.com/java-json-tools
    "com.github.java-json-tools:btf" to "Apache License, Version 2.0",
    "com.github.java-json-tools:jackson-coreutils" to "Apache License, Version 2.0",
    "com.github.java-json-tools:jackson-coreutils-equivalence" to "Apache License, Version 2.0",
    "com.github.java-json-tools:json-patch" to "Apache License, Version 2.0",
    "com.github.java-json-tools:json-schema-core" to "Apache License, Version 2.0",
    "com.github.java-json-tools:json-schema-validator" to "Apache License, Version 2.0",
    "com.github.java-json-tools:msg-simple" to "Apache License, Version 2.0",
    "com.github.java-json-tools:uri-template" to "Apache License, Version 2.0",
    // "LGPL-2.1-or-later or Apache-2.0"
    "net.java.dev.jna:jna" to "Apache License, Version 2.0",
    "net.java.dev.jna:jna-platform" to "Apache License, Version 2.0",
    // "Apache-2.0 or LGPLv3"
    "com.github.jnr:jffi" to "Apache License, Version 2.0",
    // "Apache-2.0 or EPL-2.0"
    "io.vertx:vertx-core" to "Apache License, Version 2.0",
    // "EPL-2.0 or GPL-2.0 or LGPL-2.1" — no Apache option; EPL is the permissive-most choice.
    "com.github.jnr:jnr-posix" to "Eclipse Public License - v 2.0",
)

fun licenseRequiresNoText(license: String): Boolean =
    license.lowercase().contains("public domain")

fun licenseTextResourceFor(license: String): String? {
    val normalized = license.lowercase()
    return when {
        normalized.contains("apache") -> "Apache-2.0"
        normalized.contains("mit-0") -> "MIT-0"
        normalized.contains("mit") -> "MIT"
        normalized.contains("2-clause") -> "BSD-2-Clause"
        normalized.contains("3-clause") || normalized == "bsd3" || normalized.contains("bsd") -> "BSD-3-Clause"
        normalized.contains("go license") -> "Go-License"
        normalized.contains("bouncy castle") -> "BouncyCastle"
        normalized.contains("classpath exception") -> "GPL-2.0-with-classpath-exception"
        normalized.contains("mozilla") -> "MPL-2.0"
        normalized.contains("eclipse") && normalized.contains("v 1.0") -> "EPL-1.0"
        normalized.contains("eclipse") -> "EPL-2.0"
        normalized.contains("common development") -> "CDDL-1.0"
        normalized.contains("creative commons") -> "CC0-1.0"
        else -> null
    }
}

val aggregateDependencyNotices = tasks.register("aggregateDependencyNotices") {
    val aggregatedDir = layout.buildDirectory.dir("licenses/all-notices")
    outputs.dir(aggregatedDir)
    val licenseTextsDir = layout.buildDirectory.dir("licenses/license-texts")
    outputs.dir(licenseTextsDir)

    val reportedProjects = (listOf(project) + subprojects).distinct()
    reportedProjects.forEach { p ->
        p.tasks.findByName("collectDependencyNotices")?.let { dependsOn(p.tasks.named("collectDependencyNotices")) }
        p.tasks.findByName("generateLicenseReport")?.let { dependsOn(p.tasks.named("generateLicenseReport")) }
    }

    doLast {
        val targetDir = aggregatedDir.get().asFile
        project.delete(targetDir)

        reportedProjects.forEach { sub ->
            val noticesDir = sub.layout.buildDirectory.dir("licenses/notices").get().asFile
            if (noticesDir.exists()) {
                project.copy {
                    from(noticesDir)
                    into(targetDir.resolve(sub.path.removePrefix(":").ifEmpty { sub.name }.replace(":", "/")))
                }
            }
        }

        fun collectNotices(noticeRoot: File): Map<String, Set<String>> {
            if (!noticeRoot.exists()) return emptyMap()

            val copyrights = mutableMapOf<String, MutableSet<String>>()

            noticeRoot.walkTopDown()
                .filter { it.isFile }
                .filter {
                    it.name.contains("NOTICE", true) ||
                        it.name.contains("LICENSE", true) ||
                        it.name.contains("COPYING", true)
                }
                .forEach { file ->
                    // Layout is <dependencyKey>/META-INF/<file> or <dependencyKey>/<file>.
                    val dependencyKey = when {
                        file.parentFile?.name.equals("META-INF", true) -> file.parentFile?.parentFile?.name
                        else -> file.parentFile?.name
                    } ?: return@forEach

                    val lines = runCatching { file.readLines() }.getOrElse { return@forEach }

                    val found = lines
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { it.trimStart('#', '-', '*', '/', ' ').trim() }
                        // Require an actual copyright statement. A bare "(c)" prefix also matches
                        // list markers inside license bodies (e.g. MPL's "(c) under Patent Claims"),
                        // so only accept it when a year or "Copyright"/"©" is present too.
                        .filter { line ->
                            line.startsWith("copyright", true) ||
                                line.startsWith("©") ||
                                (line.startsWith("(c)", true) && Regex("\\b(19|20)\\d{2}\\b").containsMatchIn(line))
                        }
                        .filter { line -> line.any { it.isDigit() } || line.contains("©") || line.length > 15 }
                    if (found.isNotEmpty()) copyrights.getOrPut(dependencyKey) { linkedSetOf() }.addAll(found)
                }

            return copyrights
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

        /**
         * Copyright holders for dependencies whose jars ship no NOTICE/LICENSE file.
         *
         * MIT and BSD require reproducing the copyright notice, but most publishers do not
         * embed one in the jar. The POM's <organization> / <developers> entries are the
         * authoritative fallback, and Gradle has already downloaded every POM.
         */
        fun pomCopyrights(p: Project): Map<String, String> {
            val configuration = p.configurations.findByName("runtimeClasspath")
                ?.takeIf { it.isCanBeResolved }
                ?: return emptyMap()

            val componentIds = runCatching {
                configuration.incoming.resolutionResult.allDependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .map { it.selected.id }
                    .filterIsInstance<ModuleComponentIdentifier>()
            }.getOrElse { return emptyMap() }

            val pomResults = runCatching {
                p.dependencies.createArtifactResolutionQuery()
                    .forComponents(componentIds)
                    .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                    .execute()
            }.getOrElse { return emptyMap() }

            fun holderFrom(pomText: String): String? {
                // <organization><name>X</name> wins; otherwise fall back to <developers>.
                val organization = Regex("<organization>\\s*<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
                    .find(pomText)?.groupValues?.get(1)?.trim()

                val developers = Regex("<developers>(.*?)</developers>", RegexOption.DOT_MATCHES_ALL)
                    .find(pomText)?.groupValues?.get(1)
                    ?.let { block ->
                        Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
                            .findAll(block)
                            .map { it.groupValues[1].trim() }
                            .toList()
                    }
                    .orEmpty()
                    .filter { it.isNotBlank() }

                return organization?.takeIf { it.isNotBlank() }
                    ?: developers.joinToString(", ").takeIf { it.isNotBlank() }
            }

            /**
             * Resolve and read a parent POM.
             *
             * Multi-module publishers (Netty, Google Cloud, Jedis) declare <organization> only in
             * their parent POM, so a child-only lookup finds no copyright holder at all.
             */
            fun parentPomText(pomText: String): String? {
                val parentBlock = Regex("<parent>(.*?)</parent>", RegexOption.DOT_MATCHES_ALL)
                    .find(pomText)?.groupValues?.get(1) ?: return null
                fun tag(name: String) = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
                    .find(parentBlock)?.groupValues?.get(1)?.trim()

                val group = tag("groupId") ?: return null
                val artifact = tag("artifactId") ?: return null
                val version = tag("version") ?: return null

                val parentQuery = runCatching {
                    p.dependencies.createArtifactResolutionQuery()
                        .forModule(group, artifact, version)
                        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                        .execute()
                }.getOrNull() ?: return null

                val file = parentQuery.resolvedComponents
                    .flatMap { it.getArtifacts(MavenPomArtifact::class.java) }
                    .filterIsInstance<ResolvedArtifactResult>()
                    .firstOrNull()?.file ?: return null

                return runCatching { file.readText() }.getOrNull()
            }

            val holders = mutableMapOf<String, String>()
            pomResults.resolvedComponents.forEach { component ->
                val id = component.id as? ModuleComponentIdentifier ?: return@forEach
                val pomFile = component.getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>()
                    .firstOrNull()?.file ?: return@forEach

                var pomText = runCatching { pomFile.readText() }.getOrElse { return@forEach }

                // Walk up the parent chain until a copyright holder is found (bounded depth).
                var holder = holderFrom(pomText)
                var depth = 0
                while (holder == null && depth < 5) {
                    pomText = parentPomText(pomText) ?: break
                    holder = holderFrom(pomText)
                    depth++
                }

                if (holder != null) holders["${id.group}:${id.module}"] = holder
            }
            return holders
        }

        val missingHolders = mutableSetOf<String>()
        val unresolvedLicenses = mutableSetOf<String>()

        fun renderThirdPartyMd(
            title: String,
            intro: String,
            deps: List<ThirdPartyInfo>,
            notices: Map<String, Set<String>>,
            copyrightFallback: Map<String, String>,
            bundledLicenseTexts: Set<String>,
        ): String = buildString {
            appendLine("# $title")
            appendLine()
            appendLine(intro)
            appendLine()

            if (deps.isEmpty()) {
                appendLine("* No third-party dependencies detected.")
                return@buildString
            }

            val unique = uniqueDeps(deps)
            appendLine("This distribution includes ${unique.size} third-party components, listed below with")
            appendLine("their license and copyright holder.")
            if (bundledLicenseTexts.isNotEmpty()) {
                appendLine()
                appendLine("The full text of each license referenced below is in the `LICENSES/` directory:")
                bundledLicenseTexts.sorted().forEach { appendLine("* `LICENSES/$it.txt`") }
            }
            appendLine()

            unique.forEach { dep ->
                val linkTitle = if (dep.url.isNotBlank()) {
                    "[${dep.name} ${dep.version}](${dep.url})"
                } else {
                    "${dep.name} ${dep.version}".trim()
                }

                val elected = dualLicenseElections[dep.name]
                if (elected != null) {
                    appendLine("* $linkTitle. $elected (elected; also offered under ${dep.license}).")
                } else {
                    appendLine("* $linkTitle. ${dep.license}.")
                }

                val noticeKey = "${dep.name.replace(":", ".")}-${dep.version}"
                val copyrights = notices[noticeKey].orEmpty()
                when {
                    copyrights.isNotEmpty() -> copyrights.forEach { appendLine("  - $it") }

                    // No embedded notice: fall back to the POM's declared copyright holder.
                    copyrightFallback[dep.name] != null ->
                        appendLine("  - Copyright (c) ${copyrightFallback[dep.name]}")

                    // Neither the jar nor the POM chain declares a holder. Say so explicitly
                    // rather than silently omitting the line, so the gap stays reviewable.
                    else -> {
                        appendLine("  - Copyright holder not declared by the publisher; see ${dep.url.ifBlank { "the project's own distribution" }}.")
                        missingHolders += "${dep.name}:${dep.version}"
                    }
                }
            }
        }

        /**
         * Write one canonical copy of each license actually used, into `LICENSES/`.
         *
         * Apache-2.0 section 4(a) and the MIT/BSD notice clauses require the license text itself
         * to accompany the distribution, not just the license name. Texts are shipped as build-logic
         * resources rather than harvested from jars: most publishers embed no license file, so
         * harvesting silently omits the licenses that matter most (Apache-2.0 and MIT), while
         * duplicating the ones that jars already carry into the distribution twice.
         */
        fun writeLicenseTexts(outputDir: File, deps: List<ThirdPartyInfo>): Set<String> {
            project.delete(outputDir)
            outputDir.mkdirs()

            val written = mutableSetOf<String>()
            val unresolved = mutableSetOf<String>()

            // Anonymous object solely to reach the build-logic classloader that holds the resources.
            val resourceLoader = object {}.javaClass.classLoader

            deps.map { dep -> dualLicenseElections[dep.name] ?: dep.license }
                .distinct()
                .forEach { license ->
                    if (licenseRequiresNoText(license)) return@forEach

                    val resource = licenseTextResourceFor(license)
                    if (resource == null) {
                        unresolved += license
                        return@forEach
                    }
                    if (!written.add(resource)) return@forEach

                    val stream = resourceLoader.getResourceAsStream("license-texts/$resource.txt")
                    if (stream == null) {
                        unresolved += license
                        return@forEach
                    }
                    stream.use { input ->
                        outputDir.resolve("$resource.txt").writeBytes(input.readBytes())
                    }
                }

            unresolvedLicenses += unresolved
            return written
        }

        // Combined notice for this project (and any subprojects).
        val rootNotices = collectNotices(targetDir)
        val rootDeps = reportedProjects.flatMap { sub ->
            parseDepsFromJson(sub.layout.buildDirectory.file("licenses/THIRD-PARTY-NOTICE.json").get().asFile)
        }
        val rootFallback = reportedProjects.fold(emptyMap<String, String>()) { acc, p -> acc + pomCopyrights(p) }

        val bundledLicenseTexts = writeLicenseTexts(
            licenseTextsDir.get().asFile,
            uniqueDeps(rootDeps),
        )

        layout.projectDirectory.file("THIRD-PARTY-NOTICE.md").asFile.writeText(
            renderThirdPartyMd(
                title = "Third-Party Software",
                intro = "This document lists third-party libraries distributed with ${project.name}.",
                deps = rootDeps,
                notices = rootNotices,
                copyrightFallback = rootFallback,
                bundledLicenseTexts = bundledLicenseTexts,
            )
        )

        // Per-project notices for real subprojects.
        subprojects.forEach { sub ->
            val subDeps = parseDepsFromJson(sub.layout.buildDirectory.file("licenses/THIRD-PARTY-NOTICE.json").get().asFile)
            val subNotices = collectNotices(sub.layout.buildDirectory.dir("licenses/notices").get().asFile)
            sub.projectDir.resolve("THIRD-PARTY-NOTICE.md").writeText(
                renderThirdPartyMd(
                    title = "Third-Party Software for ${sub.path}",
                    intro = "This document lists third-party libraries used by ${sub.path}.",
                    deps = subDeps,
                    notices = subNotices,
                    copyrightFallback = pomCopyrights(sub),
                    bundledLicenseTexts = emptySet(),
                )
            )
        }

        logger.lifecycle(
            "Wrote THIRD-PARTY-NOTICE.md for ${project.path}: ${uniqueDeps(rootDeps).size} components, " +
                "${bundledLicenseTexts.size} license texts in LICENSES/."
        )
        if (unresolvedLicenses.isNotEmpty()) {
            logger.warn(
                "No canonical license text bundled for ${unresolvedLicenses.size} license(s): " +
                    unresolvedLicenses.sorted().joinToString(", ") +
                    ". Add the text to build-logic/src/main/resources/license-texts and map it in licenseTextResourceFor()."
            )
        }
        if (missingHolders.isNotEmpty()) {
            logger.warn(
                "No copyright holder declared by ${missingHolders.size} dependencies " +
                    "(neither in the jar nor anywhere in the POM parent chain); " +
                    "each is marked as such in THIRD-PARTY-NOTICE.md: " +
                    missingHolders.sorted().joinToString(", ")
            )
        }
    }
}
