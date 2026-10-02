package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.*
import kotlinx.serialization.json.*

/** The same synthetic specification is read by native SwiftUI tests. No wallet or network is created. */
internal object WalletVisualFixtures {
    private val data by lazy {
        Json.parseToJsonElement(SyntheticCredentialImageFiles.read("wallet-visual-data.json").decodeToString()).jsonObject
            .also { check(it.getValue("schemaVersion").jsonPrimitive.int == 1) }
    }
    private val issuer get() = data.getValue("issuer").jsonObject
    private val credential get() = data.getValue("credential").jsonObject
    private val offered get() = data.getValue("offer").jsonObject

    val credentialSummary: CredentialSummary get() = CredentialSummary(
        id = credential.text("id"), format = credential.text("format"),
        issuer = issuer.text("identifier"), label = credential.text("title"),
        addedAt = credential.text("addedAt"),
        credentialDataJson = credential.getValue("data").toString(),
        metadataJson = buildJsonObject {
            putJsonArray("issuerDisplay") { add(buildJsonObject { put("name", issuer.text("name")); put("locale", "en") }) }
            putJsonArray("credentialDisplay") { add(buildJsonObject { put("name", credential.text("title")); put("locale", "en") }) }
        }.toString(),
    )

    val credentialDetails: CredentialDetails get() = CredentialDisplayNormalizer.toDetails(credentialSummary, listOf("en"))

    val localizedCredentialDetails: CredentialDetails get() {
        val contract = Json.parseToJsonElement(SyntheticCredentialImageFiles.read("credential-information.json").decodeToString()).jsonObject
        return CredentialDisplayNormalizer.toDetails(credentialSummary.copy(
            credentialDataJson = contract.getValue("credentialData").toString(),
            metadataJson = contract.getValue("metadata").toString(),
        ), contract.getValue("preferredLocales").jsonArray.map { it.jsonPrimitive.content })
    }

    val credentialWithImages: CredentialDetails get() = CredentialDisplayNormalizer.toDetails(
        credentialSummary.copy(credentialDataJson = buildJsonObject {
            credential.getValue("data").jsonObject.forEach { (key, value) -> put(key, value) }
            put("portrait", SyntheticCredentialImageFixtures.portraitDataUrl)
            put("signature_usual_mark", SyntheticCredentialImageFixtures.signatureDataUrl)
        }.toString()), listOf("en"),
    )

    val nearbyQrPayload: String get() = data.getValue("nearby").jsonObject.text("qrPayload")

    val partialResult: WalletDemoUiState get() {
        val outcome = data.getValue("batchOutcome").jsonObject
        return WalletDemoUiState(
            auth = WalletAuthState.Unlocked,
            session = WalletSessionState.Ready("did:example:visual", "visual-key", "{}",
                WalletDemoSigningProtection.None, listOf(credentialSummary)),
            selectedTab = WalletDemoTab.Receive,
            operation = WalletOperationState.Succeeded(outcome.text("status"), WalletDemoTab.Receive),
            lastReceivedCredentialIds = listOf(credentialSummary.id),
            deferredCredentials = listOf(WalletDemoDeferredCredential(outcome.text("pendingId"),
                outcome.text("pendingConfigurationId"), 5)),
        )
    }

    val offer: WalletDemoOfferPreview get() = WalletDemoOfferPreview(
        issuer = WalletDemoIssuerMetadata(issuer.text("identifier"), display(issuer.text("name"))),
        offeredCredentials = offered.getValue("credentials").jsonArray.map { element ->
            val item = element.jsonObject
            WalletDemoOfferedCredentialMetadata(
                configurationId = item.text("configurationId"), format = item.text("format"),
                vct = null, doctype = null,
                display = display(item.text("title")).copy(backgroundColor = item.text("backgroundColor"), textColor = "#FFFFFF"),
                claims = item.getValue("claims").jsonArray.map { claim ->
                    val value = claim.jsonObject
                    WalletDemoCredentialClaimMetadata(value.getValue("path").jsonArray.map { it.jsonPrimitive.content },
                        value.getValue("mandatory").jsonPrimitive.boolean, value.text("label"))
                },
            )
        },
        transactionCode = null,
        batchSize = offered.getValue("batchSize").jsonPrimitive.int,
    )

    val copies: Map<String, Int> get() = offered.getValue("credentials").jsonArray.associate {
        it.jsonObject.text("configurationId") to it.jsonObject.getValue("copies").jsonPrimitive.int
    }

    val payment: WalletDemoPaymentConsent get() {
        val payment = data.getValue("payment").jsonObject
        return WalletDemoPaymentConsent(
            revision = payment.text("revision"), locale = payment.text("locale"), title = payment.text("title"),
            securityHint = null, affirmativeAction = payment.text("affirmativeAction"),
            denialAction = payment.text("denialAction"), requiresUnsignedRequestWarning = true,
            fields = payment.getValue("fields").jsonArray.map {
                val field = it.jsonObject
                WalletDemoPaymentField(field.text("name"), null, field.text("value"),
                    WalletDemoPaymentFieldPlacement.valueOf(field.text("placement")))
            },
        )
    }

    val paymentReview: WalletDemoSharingReview get() {
        val ordinary = WalletDemoSharingReviewFixtures.credentialOption()
        val paymentCredential = WalletDemoSharingReviewFixtures.credentialOption(
            queryId = "payment", credentialId = "visual-payment-credential", label = "Payment authorisation",
            disclosures = listOf(WalletDemoPresentationDisclosure(
                label = "Account reference", path = "account_reference", valueJson = "\"Example account\"",
                displayValue = "Example account", selectivelyDisclosable = false,
            )),
        ).copy(format = "dc+sd-jwt")
        val base = WalletDemoSharingReviewFixtures.digitalCredentialReview(listOf(ordinary, paymentCredential))
        return base.copy(
            request = base.request.copy(transactionData = listOf(ClaimGroup(
                id = "transaction:0",
                title = "Payment", items = emptyList(), transactionType = "urn:eudi:sca:payment:1",
            ))),
            credentialRequirements = listOf(ordinary, paymentCredential).map {
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf(it.queryId)))
            },
        )
    }

    private fun display(name: String) = WalletDemoMetadataDisplay(name, null, null)
    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
}
