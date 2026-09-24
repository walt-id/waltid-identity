package id.walt.gradle.licensee

import app.cash.licensee.LicenseeExtension
import app.cash.licensee.UnusedAction
import app.cash.licensee.ViolationAction
import org.gradle.api.Project

enum class LicenseePolicy {
    Apache,
    Binary,
    Saas,
    ;

    companion object {
        fun parse(value: String): LicenseePolicy {
            val normalized = value.trim().lowercase()
            return when (normalized) {
                "apache" -> Apache
                "binary" -> Binary
                "saas" -> Saas
                else -> error(
                    "Unknown waltid.licensee.policy='$value'. Use apache, binary, or saas.",
                )
            }
        }
    }
}

data class AllowedDependency(
    val group: String,
    val artifact: String,
    val version: String,
    val reason: String,
)

object LicenseePolicies {
    private val permissiveSpdxIds = listOf(
        "Apache-2.0",
        "MIT",
        "MIT-0",
        "BSD-2-Clause",
        "BSD-3-Clause",
        "ISC",
        "0BSD",
        "CC0-1.0",
        "Unlicense",
        "NCSA",
        "Zlib",
        "BSL-1.0",
        "UPL-1.0",
        "Unicode-DFS-2016",
        "Unicode-3.0",
    )

    private val weakCopyleftSpdxIds = listOf(
        "EPL-1.0",
        "EPL-2.0",
        "CDDL-1.0",
        "CDDL-1.1",
        "MPL-2.0",
        "GPL-2.0-with-classpath-exception",
    )

    private val lgplSpdxIds = listOf(
        "LGPL-2.1-only",
        "LGPL-2.1-or-later",
        "LGPL-3.0-only",
        "LGPL-3.0-or-later",
    )

    private val gplSpdxIds = listOf(
        "GPL-2.0-only",
        "GPL-2.0-or-later",
        "GPL-3.0-only",
        "GPL-3.0-or-later",
    )

    private val allowedUrls = listOf(
        "https://www.bouncycastle.org/licence.html" to "Bouncy Castle Licence",
        "http://www.bouncycastle.org/licence.html" to "Bouncy Castle Licence",
        "https://www.bouncycastle.org/license.html" to "Bouncy Castle Licence",
        "http://www.bouncycastle.org/license.html" to "Bouncy Castle Licence",
        "https://mit-license.org/" to "MIT License",
        "http://mit-license.org/" to "MIT License",
        "https://opensource.org/license/mit" to "MIT License",
        "https://spdx.org/licenses/MIT.txt" to "MIT License",
        "https://raw.githubusercontent.com/korlibs/korge-korlibs/main/LICENSE" to "MIT License",
        "https://raw.githubusercontent.com/auth0/java-jwt/master/LICENSE" to "MIT License",
        "https://raw.githubusercontent.com/auth0/jwks-rsa-java/master/LICENSE" to "MIT License",
        "https://github.com/jimsch/COSE-JAVA/blob/master/LICENSE" to "BSD-style COSE-JAVA license",
        "https://github.com/stleary/JSON-java/blob/master/LICENSE" to "Public Domain JSON-java license",
        "http://www.creativecommons.org/publicdomain/zero/1.0/" to "CC0-1.0",
        "https://creativecommons.org/publicdomain/zero/1.0/" to "CC0-1.0",
        "http://www.eclipse.org/org/documents/edl-v10.php" to "Eclipse Distribution License 1.0",
        "https://www.eclipse.org/org/documents/edl-v10.php" to "Eclipse Distribution License 1.0",
        "http://www.mozilla.org/MPL/2.0/index.txt" to "MPL-2.0",
        "https://www.mozilla.org/MPL/2.0/index.txt" to "MPL-2.0",
        "https://projectlombok.org/LICENSE" to "MIT License",
        "https://github.com/redis/jedis/blob/master/LICENSE" to "MIT License",
        "https://github.com/googleapis/gax-java/blob/master/LICENSE" to "BSD-3-Clause",
        "https://github.com/googleapis/api-common-java/blob/main/LICENSE" to "BSD-3-Clause",
        "https://opensource.org/license/BSD-3-Clause" to "BSD-3-Clause",
        "https://asm.ow2.io/license.html" to "BSD-3-Clause",
        "http://www.sun.com/cddl/cddl.html" to "CDDL-1.0",
        "https://golang.org/LICENSE" to "BSD-style Go license",
        "https://github.com/adraffy/ENSNormalize.java/blob/main/LICENSE" to "MIT License",
        "https://github.com/TooTallNate/Java-WebSocket/blob/master/LICENSE" to "MIT License",
        "https://raw.githubusercontent.com/ThreeTen/threetenbp/main/LICENSE.txt" to "BSD-3-Clause",
        "https://aws.amazon.com/apache2.0" to "Apache-2.0",
        "http://apache.org/licenses/LICENSE-2.0.html" to "Apache-2.0",
        "https://apache.org/licenses/LICENSE-2.0.html" to "Apache-2.0",
        "https://jdbc.postgresql.org/about/license.html" to "BSD-2-Clause",
        "http://www.oracle.com/technetwork/licenses/upl-license-2927578.html" to "UPL-1.0",
    )

    /**
     * Version-pinned allowances for artifacts whose POMs omit or mis-declare a license
     * that is otherwise acceptable for every product policy. Prefer [allowUrl] when the
     * POM has a real license URL. Do not use this to paper over disallowed copyleft.
     */
    private val allowedDependencies = listOf<AllowedDependency>(
        AllowedDependency(
            group = "de.mkammerer",
            artifact = "argon2-jvm",
            version = "2.11",
            reason = "LGPL-3.0-only; already shipped in the identity stack",
        ),
        AllowedDependency(
            group = "de.mkammerer",
            artifact = "argon2-jvm-nolibs",
            version = "2.11",
            reason = "LGPL-3.0-only; already shipped in the identity stack",
        ),
        AllowedDependency(
            group = "com.mysql",
            artifact = "mysql-connector-j",
            version = "9.7.0",
            reason = "GPL-2.0 with Universal FOSS Exception, compatible with Apache-2.0 products",
        ),
        AllowedDependency(
            group = "com.microsoft.azure",
            artifact = "msal4j",
            version = "1.23.1",
            reason = "MIT License; POM omits a license URL",
        ),
        AllowedDependency(
            group = "com.microsoft.azure",
            artifact = "msal4j-persistence-extension",
            version = "1.3.0",
            reason = "MIT License; POM omits a license URL",
        ),
        AllowedDependency(
            group = "com.soywiz",
            artifact = "korlibs-annotations",
            version = "6.0.0",
            reason = "MIT License; POM omits a license URL",
        ),
    )

    fun shouldCheck(project: Project): Boolean {
        val name = project.name
        return !name.endsWith("-test") && !name.endsWith("-tests")
    }

    fun resolve(project: Project): LicenseePolicy {
        val override = project.findProperty("waltid.licensee.policy")?.toString()
        if (!override.isNullOrBlank()) {
            return LicenseePolicy.parse(override)
        }
        val path = project.path
        return when {
            path == ":waltid-license" || path.startsWith(":waltid-license-") -> LicenseePolicy.Saas
            path.startsWith(":waltid-enterprise") || path.startsWith(":waltid-credential-status") ->
                LicenseePolicy.Binary
            else -> LicenseePolicy.Apache
        }
    }

    fun spdxIdsFor(policy: LicenseePolicy): List<String> {
        val ids = (permissiveSpdxIds + weakCopyleftSpdxIds).toMutableList()
        if (policy == LicenseePolicy.Binary || policy == LicenseePolicy.Saas) {
            ids += lgplSpdxIds
        }
        if (policy == LicenseePolicy.Saas) {
            ids += gplSpdxIds
        }
        return ids
    }

    /**
     * Shadow 9.x puts a Maven 4 [org.apache.maven.api.xml.XmlService] on the plugin classpath.
     * Licensee parses POMs with maven-model-builder, which loads that service via ServiceLoader
     * using the thread context classloader. Isolated-project / included-build workers do not
     * see the provider unless we initialize it from this plugin's classloader first.
     */
    fun warmupMavenXmlService() {
        val classLoader = LicenseePolicies::class.java.classLoader
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = classLoader
        try {
            val xmlService = Class.forName("org.apache.maven.api.xml.XmlService", true, classLoader)
            val read = xmlService.methods.first { method ->
                method.name == "read" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == java.io.Reader::class.java
            }
            read.invoke(null, java.io.StringReader("<x/>"))
        } finally {
            thread.contextClassLoader = previous
        }
    }

    fun configure(project: Project, licensee: LicenseeExtension) {
        warmupMavenXmlService()
        val policy = resolve(project)
        spdxIdsFor(policy).forEach { licensee.allow(it) }
        allowedUrls.forEach { (url, reason) ->
            licensee.allowUrl(url) {
                because(reason)
            }
        }
        allowedDependencies.forEach { dependency ->
            licensee.allowDependency(dependency.group, dependency.artifact, dependency.version) {
                because(dependency.reason)
            }
        }
        licensee.unusedAction(UnusedAction.IGNORE)
        licensee.violationAction(ViolationAction.FAIL)
        project.logger.info("Configured Licensee policy {} for {}", policy, project.path)
    }
}
