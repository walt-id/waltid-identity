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

    val readerTrustImport: id.walt.wallet2.mobile.ProximityReaderTrustImportPreview get() {
        val value = data.getValue("readerTrust").jsonObject
        return id.walt.wallet2.mobile.ProximityReaderTrustImportPreview(
            id.walt.wallet2.mobile.ProximityReaderTrustImportKind.ReaderCa, value.text("sourceName"),
            listOf(id.walt.wallet2.mobile.ProximityReaderTrustAnchorPreview(value.text("displayName"),
                value.text("subject"), value.text("issuer"), value.text("fingerprint"),
                kotlin.time.Instant.parse(value.text("validFrom")), kotlin.time.Instant.parse(value.text("validUntil")))),
            emptyList(), id.walt.wallet2.mobile.ProximityReaderTrustSettings(id.walt.wallet2.mobile.ProximityReaderPolicy.RequireTrusted),
        )
    }

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

    val keySetup: WalletDemoIdentitySetup.Choose get() = WalletDemoIdentitySetup.Choose(
        data.getValue("keySetup").jsonObject.getValue("options").jsonArray.map { value ->
            val item = value.jsonObject
            fun choice(name: String) = item.getValue(name).jsonObject.let {
                WalletDemoKeyChoice(it.text("id"), it.text("title"), it.text("detail"))
            }
            WalletDemoKeySetupOption(item.text("id"), choice("recovery"), choice("storage"), choice("approval"))
        })

    val nearbyReviewData get() = data.getValue("nearby").jsonObject.getValue("review").jsonObject

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
                outcome.text("pendingConfigurationId"), 5, status = WalletDemoContinuationStatus.AwaitingIssuer,
                displayMetadataJson = outcome.getValue("pendingMetadata").toString())),
            issuanceReceipt = WalletDemoIssuanceReceipt(offer.issuer, setOf(outcome.text("pendingId"))),
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

    val payment: WalletDemoPaymentConsent get() = payment("payment")
    val localizedPayment: WalletDemoPaymentConsent get() = payment("paymentLocalized")
    private fun payment(key: String): WalletDemoPaymentConsent {
        val payment = data.getValue(key).jsonObject
        return WalletDemoPaymentConsent(
            revision = payment.text("revision"), locale = payment.text("locale"), title = payment["title"]?.jsonPrimitive?.contentOrNull,
            securityHint = payment["securityHint"]?.jsonPrimitive?.contentOrNull, affirmativeAction = payment.text("affirmativeAction"),
            denialAction = payment["denialAction"]?.jsonPrimitive?.contentOrNull,
            requiresUnsignedRequestWarning = payment["requiresUnsignedRequestWarning"]?.jsonPrimitive?.booleanOrNull ?: true,
            fields = payment.getValue("fields").jsonArray.map {
                val field = it.jsonObject
                WalletDemoPaymentField(field.text("name"), null, field.text("value"),
                    WalletDemoPaymentFieldPlacement.valueOf(field.text("placement")))
            },
        )
    }

    val sharingCredentials: List<WalletDemoPresentationCredentialOption> get() =
        data.getValue("sharing").jsonObject.getValue("credentials").jsonArray.map { item ->
            val credential = item.jsonObject
            WalletDemoPresentationCredentialOption(
                queryId = credential.text("queryId"), credentialId = credential.text("credentialId"),
                label = credential.text("title"), issuer = credential.text("issuer"), format = credential.text("format"),
                credentialDataJson = "{}",
                disclosures = credential.getValue("disclosures").jsonArray.map {
                    val claim = it.jsonObject
                    WalletDemoPresentationDisclosure(label = claim.text("label"), path = claim.text("path"),
                        valueJson = JsonPrimitive(claim.text("value")).toString(), displayValue = claim.text("value"),
                        selectivelyDisclosable = false)
                },
            )
        }

    val providerReview: WalletDemoSharingReview get() =
        WalletDemoSharingReviewFixtures.annexCReview(WalletDemoReaderTrust.PendingVerification,
            listOf(sharingCredentials.first()))

    val paymentReview: WalletDemoSharingReview get() {
        val options = sharingCredentials
        val base = WalletDemoSharingReviewFixtures.digitalCredentialReview(options)
        return base.copy(
            request = base.request.copy(transactionData = listOf(ClaimGroup(
                id = "transaction:0",
                title = "Payment", items = emptyList(), transactionType = "urn:eudi:sca:payment:1",
            ))),
            credentialRequirements = options.map {
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf(it.queryId)))
            },
        )
    }

    private fun display(name: String) = WalletDemoMetadataDisplay(name, null, null)
    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
}
