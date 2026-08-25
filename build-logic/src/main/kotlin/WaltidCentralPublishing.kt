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
     * Projects outside `:waltid-libraries` that are still published to Maven Central.
     *
     * `waltid-service-commons` is a required `api` dependency of public libraries, so it has to
     * be resolvable from Maven Central for those libraries to be consumable.
     */
    private val ADDITIONAL_PROJECT_PATHS: Set<String> = setOf(
        ":waltid-services:waltid-service-commons",
    )

    /**
     * Reviewed exclusions within `:waltid-libraries`.
     *
     * Each entry needs a reason. Do not add entries without one.
     */
    val EXCLUDED_PROJECT_PATHS: Set<String> = setOf(
        // Example/demo code, not a reusable library.
        ":waltid-libraries:credentials:waltid-digital-credentials-examples",

        // Runnable server modules rather than reusable libraries.
        ":waltid-libraries:protocols:waltid-openid4vc-wallet-server",
        ":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-server",
        ":waltid-libraries:protocols:waltid-openid4vp-verifier-openapi",
    )

    /** True when [projectPath] should be published to Maven Central. */
    fun isCentralArtifact(projectPath: String): Boolean =
        (projectPath.startsWith(LIBRARIES_PREFIX) || projectPath in ADDITIONAL_PROJECT_PATHS) &&
            projectPath !in EXCLUDED_PROJECT_PATHS
}
