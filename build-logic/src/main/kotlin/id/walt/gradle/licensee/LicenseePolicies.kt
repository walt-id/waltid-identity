package id.walt.gradle.licensee

import app.cash.licensee.LicenseeExtension
import app.cash.licensee.UnusedAction
import app.cash.licensee.ViolationAction
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

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

object LicenseePolicies {
    // Named because they repeat across the SPDX list and the URL allow-list below: BSD-3-Clause is both an
    // SPDX identifier and the name reported for several URLs, and MIT_LICENSE_NAME is the name the POMs of
    // eleven dependencies declare. The SPDX identifier for MIT is the separate "MIT" entry below.
    private const val BSD_3_CLAUSE = "BSD-3-Clause"
    private const val MIT_LICENSE_NAME = "MIT License"

    private val permissiveSpdxIds = listOf(
        "Apache-2.0",
        "MIT",
        "MIT-0",
        "BSD-2-Clause",
        BSD_3_CLAUSE,
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
        // Java classpath exception used by Jakarta APIs. This is not GPL-2.0-only.
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
        "https://mit-license.org/" to MIT_LICENSE_NAME,
        "http://mit-license.org/" to MIT_LICENSE_NAME,
        "https://opensource.org/license/mit" to MIT_LICENSE_NAME,
        "https://spdx.org/licenses/MIT.txt" to MIT_LICENSE_NAME,
        "https://raw.githubusercontent.com/korlibs/korge-korlibs/main/LICENSE" to MIT_LICENSE_NAME,
        "https://raw.githubusercontent.com/auth0/java-jwt/master/LICENSE" to MIT_LICENSE_NAME,
        "https://raw.githubusercontent.com/auth0/jwks-rsa-java/master/LICENSE" to MIT_LICENSE_NAME,
        "https://github.com/jimsch/COSE-JAVA/blob/master/LICENSE" to "BSD-style COSE-JAVA license",
        "https://github.com/stleary/JSON-java/blob/master/LICENSE" to "Public Domain JSON-java license",
        "http://www.creativecommons.org/publicdomain/zero/1.0/" to "CC0-1.0",
        "https://creativecommons.org/publicdomain/zero/1.0/" to "CC0-1.0",
        "http://www.eclipse.org/org/documents/edl-v10.php" to "Eclipse Distribution License 1.0",
        "https://www.eclipse.org/org/documents/edl-v10.php" to "Eclipse Distribution License 1.0",
        "http://www.mozilla.org/MPL/2.0/index.txt" to "MPL-2.0",
        "https://www.mozilla.org/MPL/2.0/index.txt" to "MPL-2.0",
        "https://projectlombok.org/LICENSE" to MIT_LICENSE_NAME,
        "https://github.com/redis/jedis/blob/master/LICENSE" to MIT_LICENSE_NAME,
        "https://github.com/googleapis/gax-java/blob/master/LICENSE" to BSD_3_CLAUSE,
        "https://github.com/googleapis/api-common-java/blob/main/LICENSE" to BSD_3_CLAUSE,
        "https://opensource.org/license/BSD-3-Clause" to BSD_3_CLAUSE,
        "https://asm.ow2.io/license.html" to BSD_3_CLAUSE,
        "http://www.sun.com/cddl/cddl.html" to "CDDL-1.0",
        "https://golang.org/LICENSE" to "BSD-style Go license",
        "https://github.com/adraffy/ENSNormalize.java/blob/main/LICENSE" to MIT_LICENSE_NAME,
        "https://github.com/TooTallNate/Java-WebSocket/blob/master/LICENSE" to MIT_LICENSE_NAME,
        "https://raw.githubusercontent.com/ThreeTen/threetenbp/main/LICENSE.txt" to BSD_3_CLAUSE,
        "https://aws.amazon.com/apache2.0" to "Apache-2.0",
        "http://apache.org/licenses/LICENSE-2.0.html" to "Apache-2.0",
        "https://apache.org/licenses/LICENSE-2.0.html" to "Apache-2.0",
        "https://jdbc.postgresql.org/about/license.html" to "BSD-2-Clause",
        "http://www.oracle.com/technetwork/licenses/upl-license-2927578.html" to "UPL-1.0",
    )

    /**
     * GNU's HTML page for LGPL-3.0. Licensee's SPDX map only knows the standalone and .txt URLs,
     * so LGPL-3.0-only does not match argon2-jvm's POM.
     */
    private const val LGPL_3_HTML = "https://www.gnu.org/licenses/lgpl-3.0.en.html"

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
        allowArgon2(project, licensee, policy)
        ignoreNameOnlyMitCoordinates(licensee)
        allowWalletMysqlConnector(project, licensee, policy)
        licensee.unusedAction(UnusedAction.IGNORE)
        licensee.violationAction(ViolationAction.FAIL)
        project.logger.info("Configured Licensee policy {} for {}", policy, project.path)
    }

    /**
     * Binary and SaaS already accept LGPL, so the unrecognized HTML URL is enough and does not
     * track a version. Apache does not accept LGPL in general; argon2 stays a coordinate
     * exception whose version is the `argon2-jvm` catalog entry.
     */
    private fun allowArgon2(project: Project, licensee: LicenseeExtension, policy: LicenseePolicy) {
        if (policy == LicenseePolicy.Binary || policy == LicenseePolicy.Saas) {
            licensee.allowUrl(LGPL_3_HTML) {
                because("LGPL-3.0-only. Licensee does not map GNU's HTML license page to the SPDX id.")
            }
            return
        }
        val version = project.identityCatalogVersion("argon2-jvm") ?: return
        val reason = "LGPL-3.0-only already shipped by the identity stack. Version follows the argon2-jvm catalog entry."
        licensee.allowDependency("de.mkammerer", "argon2-jvm", version) {
            because(reason)
        }
        licensee.allowDependency("de.mkammerer", "argon2-jvm-nolibs", version) {
            because(reason)
        }
    }

    /**
     * These POMs say "MIT License" and include no URL, in every release checked through msal4j 1.26.0.
     * [LicenseeExtension.allowDependency] requires a version, so a pin fails on every Azure SDK bump
     * without a license change. Ignoring the coordinate keeps the version free to move.
     */
    private fun ignoreNameOnlyMitCoordinates(licensee: LicenseeExtension) {
        listOf("msal4j", "msal4j-persistence-extension").forEach { artifact ->
            licensee.ignoreDependencies("com.microsoft.azure", artifact) {
                because("MIT License with no URL or SPDX identifier.")
            }
        }
    }

    /**
     * MySQL Connector/J is GPL-2.0-only WITH Universal-FOSS-exception-1.0. The POM has that name
     * and no URL, so allowing GPL-2.0-only would not match it and would also accept plain GPLv2.
     * The FOSS exception permits combination with an Apache-2.0 work. It does not permit shipping
     * the connector inside a proprietary binary, so the binary policy never allows this coordinate.
     */
    private fun allowWalletMysqlConnector(
        project: Project,
        licensee: LicenseeExtension,
        policy: LicenseePolicy,
    ) {
        if (policy == LicenseePolicy.Binary || !project.path.endsWith(":waltid-wallet-api")) return
        val version = project.identityCatalogVersion("mysql")
            ?: error("waltid-wallet-api requires catalog version 'mysql' to allow mysql-connector-j")
        licensee.allowDependency("com.mysql", "mysql-connector-j", version) {
            because(
                "GPL-2.0-only WITH Universal-FOSS-exception-1.0 for the Apache-2.0 wallet service. " +
                    "Version follows the mysql catalog entry. Not allowed on the binary policy.",
            )
        }
    }

    private fun Project.identityCatalogVersion(name: String): String? {
        val catalogs = extensions.findByType(VersionCatalogsExtension::class.java) ?: return null
        val catalog = catalogs.find("identityLibs").orElse(null) ?: return null
        return catalog.findVersion(name).orElse(null)?.requiredVersion
    }
}
