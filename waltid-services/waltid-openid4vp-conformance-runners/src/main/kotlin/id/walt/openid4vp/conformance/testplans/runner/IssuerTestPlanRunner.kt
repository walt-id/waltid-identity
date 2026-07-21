package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.http.IssuerInterface
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariant
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantModuleRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunStatus
import id.walt.openid4vp.conformance.testplans.runner.req.IssuerTestPlanConfiguration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Runner for OpenID4VCI Issuer conformance tests.
 *
 * In this scenario:
 * - Our issuer is the System Under Test (SUT)
 * - The conformance suite acts as a wallet
 * - The conformance suite will call our issuer endpoints
 */
class IssuerTestPlanRunner(
    val config: IssuerTestPlanConfiguration,
    val conformance: ConformanceInterface,
    val issuerInterface: IssuerInterface? = null
) {
    private val conformanceHost = conformance.conformanceHost
    private val conformancePort = conformance.conformancePort
    private val moduleSelection = IssuerModuleSelection.fromEnvironment()
    private val excludePreAuthorizedMultipleClients = System.getenv(EXCLUDE_PREAUTH_MULTIPLE_CLIENTS_ENV)
        ?.equals("true", ignoreCase = true) == true
    private val browserAutomationConfig = IssuerBrowserAutomationConfig.fromEnvironment()
    private val browserAutomation = IssuerConformanceBrowserAutomation(
        config = browserAutomationConfig,
        conformanceHost = conformanceHost,
        conformancePort = conformancePort,
    )

    init {
        if (browserAutomationConfig.enabled) {
            println(
                "Issuer browser automation enabled " +
                    "(username=${browserAutomationConfig.username}, timeout=${browserAutomationConfig.timeoutSeconds}s)"
            )
        } else {
            println("Issuer browser automation disabled")
        }
        if (moduleSelection.isActive) {
            println("Issuer module filtering enabled: ${moduleSelection.description}")
        }
    }

    suspend fun attempt(variant: IssuerVariant): IssuerVariantRunResult {
        println("-- Conformance OID4VCI Issuer Matrix Variant ${variant.id} -- -> Setup")

        val variantJson = variant.toJsonObject()
        if (config.credentialOfferAuthMethod != null && issuerInterface == null) {
            return IssuerVariantRunResult(
                variantId = variant.id,
                variant = variantJson,
                status = IssuerVariantRunStatus.BLOCKED,
                error = "Variant requires credential offers, but no issuer management interface is configured."
            )
        }

        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig(config.testPlanCreationUrl)
        println("Creating issuer test plan for variant ${variant.id}... ($createTestPlanUrl)")
        val testPlanConfig = config.withStaticTxCode()

        val createTestPlanResponse = runCatching {
            conformance.createTestPlan(createTestPlanUrl, testPlanConfig)
        }.getOrElse {
            return IssuerVariantRunResult(
                variantId = variant.id,
                variant = variantJson,
                status = classifyPlanCreationFailure(it),
                error = it.compactMessage()
            )
        }

        if (createTestPlanResponse.modules.isEmpty()) {
            return IssuerVariantRunResult(
                variantId = variant.id,
                variant = variantJson,
                status = IssuerVariantRunStatus.NOT_APPLICABLE,
                planId = createTestPlanResponse.id,
                error = "Conformance suite returned no modules for this variant."
            )
        }

        val testPlanId = createTestPlanResponse.id
        println("Created test plan: $testPlanId")
        println("The conformance suite will call issuer: ${config.issuerUrl}")

        val selectedModules = moduleSelection.filter(createTestPlanResponse.modules) { it.testModule }
        val modulesToRun = selectedModules.filterNot { module ->
            excludePreAuthorizedMultipleClients &&
                variant.grantType == PRE_AUTHORIZATION_CODE &&
                module.testModule == MULTIPLE_CLIENTS_MODULE
        }
        if (modulesToRun.size != selectedModules.size) {
            println(
                "Excluding $MULTIPLE_CLIENTS_MODULE for pre_authorization_code because the " +
                    "upstream module reuses client 1's consumed pre-authorized code for client 2."
            )
        }
        if (modulesToRun.isEmpty()) {
            return IssuerVariantRunResult(
                variantId = variant.id,
                variant = variantJson,
                status = IssuerVariantRunStatus.NOT_APPLICABLE,
                planId = testPlanId,
                error = "No conformance modules matched ${moduleSelection.description}."
            )
        }

        println("Running ${modulesToRun.size}/${createTestPlanResponse.modules.size} modules")
        modulesToRun.forEach { println("   - ${it.testModule}") }

        val moduleResults = modulesToRun.map { module ->
            runModuleAttempt(testPlanId, module.testModule, module.variant)
        }

        return IssuerVariantRunResult(
            variantId = variant.id,
            variant = variantJson,
            status = overallStatus(moduleResults),
            planId = testPlanId,
            modules = moduleResults,
        )
    }

    private suspend fun runModuleAttempt(
        testPlanId: String,
        testModule: String,
        moduleVariant: JsonObject,
    ): IssuerVariantModuleRunResult {
        var testId: String? = null
        val logUrlForTest: (String) -> String = { "https://$conformanceHost:$conformancePort/log-detail.html?log=$it" }

        return runCatching {
            val createTestUrl = conformance.buildCreateTestUrl(testPlanId, testModule, moduleVariant)
            println("Creating test for module $testModule... ($createTestUrl)")
            val createTestResponse = conformance.createTest(createTestUrl)
            testId = createTestResponse.id

            println("Created test: $testId")
            println("View test run at: ${logUrlForTest(testId!!)}")
            println("Waiting for conformance suite to complete issuer test...")

            waitForIssuerTestCompletion(testId!!, credentialOfferProviderFor(testModule))

            val testRunInfo = conformance.getTestRunInfo(testId!!)
            println("Module $testModule finished with status=${testRunInfo.status}, result=${testRunInfo.result}")

            val accepted = acceptsModuleResult(testModule, testRunInfo.status, testRunInfo.result)
            IssuerVariantModuleRunResult(
                testModule = testModule,
                testId = testId,
                logUrl = logUrlForTest(testId!!),
                status = testRunInfo.status,
                result = testRunInfo.result,
                accepted = accepted,
                variant = moduleVariant,
            )
        }.getOrElse { throwable ->
            val latestInfo = testId?.let { id -> runCatching { conformance.getTestRunInfo(id) }.getOrNull() }
            IssuerVariantModuleRunResult(
                testModule = testModule,
                testId = testId,
                logUrl = testId?.let(logUrlForTest),
                status = latestInfo?.status,
                result = latestInfo?.result,
                accepted = false,
                error = throwable.compactMessage(),
                variant = moduleVariant,
            )
        }
    }

    private fun acceptsModuleResult(testModule: String, status: String, result: String?): Boolean {
        if (status == "FINISHED" && result == "PASSED") {
            return true
        }

        return testModule in config.skippableModules &&
            status in setOf("FINISHED", "INTERRUPTED") &&
            result == "SKIPPED"
    }

    private fun overallStatus(moduleResults: List<IssuerVariantModuleRunResult>): IssuerVariantRunStatus {
        if (moduleResults.all { it.accepted }) {
            return IssuerVariantRunStatus.PASSED
        }

        return if (moduleResults.any { it.error?.isBlockedError() == true || it.status == "WAITING" }) {
            IssuerVariantRunStatus.BLOCKED
        } else {
            IssuerVariantRunStatus.FAILED
        }
    }

    private fun classifyPlanCreationFailure(throwable: Throwable): IssuerVariantRunStatus {
        val message = throwable.compactMessage().lowercase()
        return when {
            "no test modules" in message || "not applicable" in message -> IssuerVariantRunStatus.NOT_APPLICABLE
            "unknown variant" in message || "invalid variant" in message || "unsupported variant" in message ->
                IssuerVariantRunStatus.SUITE_INVALID
            "missing" in message || "required" in message || "config" in message || "credential_offer" in message ->
                IssuerVariantRunStatus.BLOCKED
            else -> IssuerVariantRunStatus.SUITE_INVALID
        }
    }

    private fun String.isBlockedError(): Boolean {
        val message = lowercase()
        return "waiting" in message ||
            "user interaction" in message ||
            "oauth login" in message ||
            "credential offer" in message ||
            "timeout" in message
    }

    private fun Throwable.compactMessage(): String =
        listOfNotNull(javaClass.simpleName, message).joinToString(": ")

    private companion object {
        const val EXCLUDE_PREAUTH_MULTIPLE_CLIENTS_ENV =
            "OPENID4VCI_CONFORMANCE_EXCLUDE_PREAUTH_MULTIPLE_CLIENTS"
        const val MULTIPLE_CLIENTS_MODULE = "oid4vci-1_0-issuer-happy-flow-multiple-clients"
        const val PRE_AUTHORIZATION_CODE = "pre_authorization_code"
    }

    private fun credentialOfferProviderFor(testModule: String): (suspend () -> String)? {
        val authMethod = config.credentialOfferAuthMethod ?: return null

        return suspend {
            val issuer = requireNotNull(issuerInterface) {
                "Test module $testModule requires a credential offer, but no issuer management interface is configured."
            }
            println("Creating fresh issuer2 credential offer for module $testModule...")
            val offerResponse = issuer.createCredentialOffer(
                profileId = config.credentialProfileId,
                authMethod = authMethod,
                staticTxCode = config.staticTxCode,
            )
            println("Created credential offer: ${offerResponse.offerId}")
            println("Credential offer URI: ${offerResponse.credentialOffer}")
            offerResponse.txCodeValue?.let { println("Credential offer tx_code: $it") }
            offerResponse.credentialOffer
        }
    }

    private suspend fun waitForIssuerTestCompletion(
        testId: String,
        credentialOfferProvider: (suspend () -> String)? = null,
    ) {
        var counter = 0
        val attemptedBrowserUrls = mutableSetOf<String>()
        val attemptedCredentialOfferEndpoints = mutableSetOf<String>()
        while (true) {
            counter++
            val testRunInfo = conformance.getTestRunInfo(testId)
            println("Current conformance test status: ${testRunInfo.status}")

            if (testRunInfo.status in setOf("FINISHED", "INTERRUPTED")) {
                return
            }

            if (testRunInfo.status == "WAITING" && browserAutomationConfig.enabled) {
                if (attemptBrowserAutomation(testId, attemptedBrowserUrls, shouldLog = counter == 1 || counter % 10 == 0)) {
                    counter = 0
                }
            }

            if (testRunInfo.status == "WAITING" && credentialOfferProvider != null) {
                if (attemptCredentialOfferDelivery(testId, credentialOfferProvider, attemptedCredentialOfferEndpoints, shouldLog = counter == 1 || counter % 10 == 0)) {
                    counter = 0
                }
            }

            if (counter > 60) {
                if (testRunInfo.status == "WAITING") {
                    throw IllegalStateException(
                        "Test $testId is stuck in WAITING status after ${counter - 1} seconds. " +
                        "This typically means the test requires user interaction (OAuth login). " +
                        "Please complete the test manually at https://$conformanceHost:$conformancePort/test-info/$testId " +
                        "or set OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION=true to let Playwright complete the login."
                    )
                }
                throw IllegalStateException("Waited for issuer test $testId for ${counter - 1} seconds, but it is still ${testRunInfo.status}")
            }

            kotlinx.coroutines.delay(1_000)
        }
    }

    private suspend fun attemptCredentialOfferDelivery(
        testId: String,
        credentialOfferProvider: suspend () -> String,
        attemptedCredentialOfferEndpoints: MutableSet<String>,
        shouldLog: Boolean,
    ): Boolean {
        val testRun = runCatching { conformance.getTestRun(testId) }
            .getOrElse {
                if (shouldLog) {
                    println("Credential offer delivery is pending, but test run details are not available yet: ${it.compactMessage()}")
                }
                return false
            }

        val credentialOfferEndpoint = testRun.exposed["credential_offer_endpoint"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: run {
                if (shouldLog) {
                    println("Credential offer delivery is pending, but the suite has not exposed credential_offer_endpoint yet.")
                }
                return false
            }

        if (credentialOfferEndpoint in attemptedCredentialOfferEndpoints) {
            if (shouldLog) {
                println("Credential offer was already delivered to $credentialOfferEndpoint; waiting for suite status to change.")
            }
            return false
        }

        val credentialOffer = credentialOfferProvider()
        val delivery = credentialOffer.toConformanceCredentialOfferDelivery()
        println("Delivering issuer credential offer to conformance suite: $credentialOfferEndpoint")
        conformance.deliverCredentialOffer(
            credentialOfferEndpoint = credentialOfferEndpoint,
            parameterName = delivery.parameterName,
            parameterValue = delivery.parameterValue,
        )
        attemptedCredentialOfferEndpoints += credentialOfferEndpoint
        return true
    }

    private suspend fun attemptBrowserAutomation(
        testId: String,
        attemptedBrowserUrls: MutableSet<String>,
        shouldLog: Boolean,
    ): Boolean {
        val testRun = runCatching { conformance.getTestRun(testId) }
            .getOrElse {
                if (shouldLog) {
                    println("Browser automation is enabled, but test run details are not available yet: ${it.compactMessage()}")
                }
                return false
            }

        val interactions = testRun.browserInteractionsForAutomation()
        if (interactions.isEmpty()) {
            if (shouldLog) {
                println(
                    "Browser automation is enabled, but the conformance suite has not exposed a browser URL yet. " +
                        testRun.browserInteractionSummary()
                )
            }
            return false
        }

        val interaction = interactions
            .firstOrNull { it.url !in attemptedBrowserUrls }
            ?: run {
                if (shouldLog) {
                    println(
                        "Browser automation already attempted all exposed browser URLs for test $testId. " +
                            "Waiting for suite status to change. ${testRun.browserInteractionSummary()}"
                    )
                }
                return false
            }

        attemptedBrowserUrls += interaction.url
        println("Browser automation enabled; completing conformance browser interaction for test $testId")

        runCatching {
            conformance.markBrowserUrlVisited(testId, interaction.url)
        }.onFailure {
            println("Warning: could not mark conformance browser URL as visited: ${it.compactMessage()}")
        }

        browserAutomation.complete(interaction)

        return true
    }

    private data class CredentialOfferDelivery(
        val parameterName: String,
        val parameterValue: String,
    )

    private fun String.toConformanceCredentialOfferDelivery(): CredentialOfferDelivery {
        val offer = trim()
        require(offer.isNotBlank()) {
            "Credential offer is blank"
        }

        if (offer.startsWith("{")) {
            return CredentialOfferDelivery("credential_offer", offer)
        }

        if (offer.startsWith("https://", ignoreCase = true)) {
            return CredentialOfferDelivery("credential_offer_uri", offer)
        }

        if (offer.startsWith("openid-credential-offer://", ignoreCase = true)) {
            val params = parseQueryParameters(URI(offer).rawQuery)
            val byValue = params["credential_offer"]
            val byReference = params["credential_offer_uri"]

            require(!(byValue != null && byReference != null)) {
                "Credential offer deep link contains both credential_offer and credential_offer_uri"
            }

            return when {
                !byValue.isNullOrBlank() -> CredentialOfferDelivery("credential_offer", byValue)
                !byReference.isNullOrBlank() -> CredentialOfferDelivery("credential_offer_uri", byReference)
                else -> error("Credential offer deep link contains neither credential_offer nor credential_offer_uri")
            }
        }

        error("Unsupported credential offer format. Expected raw JSON, HTTPS credential_offer_uri, or openid-credential-offer deep link.")
    }

    private fun parseQueryParameters(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }

        return rawQuery.split("&")
            .mapNotNull { pair ->
                if (pair.isBlank()) {
                    null
                } else {
                    val index = pair.indexOf("=")
                    val rawName = if (index >= 0) pair.substring(0, index) else pair
                    val rawValue = if (index >= 0) pair.substring(index + 1) else ""
                    decodeQueryPart(rawName) to decodeQueryPart(rawValue)
                }
            }
            .toMap()
    }

    private fun decodeQueryPart(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)
}

private data class IssuerModuleSelection(
    val groups: Set<String> = emptySet(),
    val modules: Set<String> = emptySet(),
) {
    val isActive: Boolean
        get() = groups.isNotEmpty() || modules.isNotEmpty()

    val description: String
        get() = buildList {
            if (groups.isNotEmpty()) {
                add("groups=${groups.joinToString(",")}")
            }
            if (modules.isNotEmpty()) {
                add("modules=${modules.joinToString(",")}")
            }
        }.joinToString("; ").ifBlank { "all modules" }

    fun <T> filter(items: List<T>, moduleName: (T) -> String): List<T> {
        if (!isActive) {
            return items
        }

        return items.filter { item ->
            val name = moduleName(item)
            groups.matchesGroup(name) && modules.matchesName(name)
        }
    }

    private fun Set<String>.matchesGroup(moduleName: String): Boolean =
        isEmpty() || groupFor(moduleName) in this

    private fun Set<String>.matchesName(moduleName: String): Boolean =
        isEmpty() || moduleName in this

    companion object {
        private val allowedGroups = setOf("metadata", "positive", "negative")

        private val metadataModules = setOf(
            "oid4vci-1_0-issuer-metadata-test",
            "oid4vci-1_0-issuer-metadata-test-signed",
        )

        private val positiveModules = setOf(
            "oid4vci-1_0-issuer-happy-flow",
            "oid4vci-1_0-issuer-happy-flow-additional-requests",
            "oid4vci-1_0-issuer-happy-flow-multiple-clients",
            "oid4vci-1_0-issuer-happy-flow-skip-notification",
            "oid4vci-1_0-issuer-batch-issuance",
        )

        fun fromEnvironment(): IssuerModuleSelection {
            val groups = csv("OPENID4VCI_CONFORMANCE_MODULE_GROUPS")
                .let { if ("all" in it) emptySet() else it }
            val unknownGroups = groups - allowedGroups
            require(unknownGroups.isEmpty()) {
                "Unsupported OPENID4VCI_CONFORMANCE_MODULE_GROUPS values: ${unknownGroups.joinToString(",")}. " +
                    "Supported values: ${allowedGroups.joinToString(",")}, all"
            }

            return IssuerModuleSelection(
                groups = groups,
                modules = csv("OPENID4VCI_CONFORMANCE_MODULES"),
            )
        }

        private fun groupFor(moduleName: String): String =
            when {
                moduleName in metadataModules -> "metadata"
                moduleName in positiveModules -> "positive"
                moduleName.startsWith("oid4vci-1_0-issuer-fail-") -> "negative"
                else -> "other"
            }

        private fun csv(name: String): Set<String> = System.getenv(name)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }
}
