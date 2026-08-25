/**
 * Selects which Gradle projects are published to Maven Central.
 *
 * Publishing to `https://maven.waltid.dev` is unaffected by this and stays enabled for every
 * module that applies `waltid.publish.maven`.
 *
 * Maven Central distribution is limited to the reusable public libraries under
 * `:waltid-libraries`. Applications, services, examples and server modules are deliberately
 * excluded: Maven Central coordinates are immutable, so the set is kept deliberately small
 * and reviewed.
 *
 * The resolved set is printed by `scripts/release-maven-central-local.sh` before any upload,
 * so an accidental expansion is visible to the release operator.
 */
object WaltidCentralPublishing {

    private const val LIBRARIES_PREFIX = ":waltid-libraries:"

    /**
     * Reviewed exclusions within `:waltid-libraries`.
     *
     * Each entry needs a reason. Do not add entries without one.
     */
    val EXCLUDED_PROJECT_PATHS: Set<String> = setOf(
        // Example/demo code, not a reusable library.
        ":waltid-libraries:credentials:waltid-digital-credentials-examples",

        // Runnable server modules rather than libraries, and they depend on
        // `:waltid-services:waltid-service-commons`, which is not a Maven Central artifact.
        ":waltid-libraries:protocols:waltid-openid4vc-wallet-server",
        ":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-server",
        ":waltid-libraries:protocols:waltid-openid4vp-verifier-openapi",

        // Depends on `:waltid-services:waltid-service-commons` (runtime scope in the generated
        // POM), which is not published to Maven Central. Revisit if service-commons is added.
        ":waltid-libraries:auth:waltid-ktor-authnz",
    )

    /** True when [projectPath] should be published to Maven Central. */
    fun isCentralArtifact(projectPath: String): Boolean =
        projectPath.startsWith(LIBRARIES_PREFIX) && projectPath !in EXCLUDED_PROJECT_PATHS
}
