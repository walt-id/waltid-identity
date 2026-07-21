package id.walt.walletdemo.compose.android

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import id.walt.wallet2.mobile.AndroidDigitalCredentialProvider
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletAnnexCPreview
import id.walt.wallet2.mobile.MobileWalletAnnexCRequest
import id.walt.wallet2.mobile.MobileWalletAnnexCSubmission
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialProtocols
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialPreview
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialOption
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.data.WalletX509TrustConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Credential Manager provider UI owned by the demo app, not by the reusable KMP SDK. */
class DigitalCredentialProviderActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            runCatching {
                val allowlist = assets.open("privileged_apps.json").bufferedReader().use { it.readText() }
                val demoTrustAnchor = assets.open("dc_api_demo_trust_anchor.pem").bufferedReader().use { it.readText() }
                val input = AndroidDigitalCredentialProvider.extract(intent, allowlist)
                val wallet = MobileWalletFactory(applicationContext).create(
                    MobileWalletConfig(
                        requestObjectX509Trust = WalletX509TrustConfig(
                            trustAnchorPemCertificates = listOf(demoTrustAnchor),
                            allowedRequestObjectAlgorithms = setOf("ES256", "ES384", "ES512"),
                        )
                    )
                )
                wallet.bootstrap()
                if (input.request.protocol == MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C) {
                    val data = Json.parseToJsonElement(input.request.dataJson).jsonObject
                    val deviceRequest = requireNotNull(data["deviceRequest"]?.jsonPrimitive?.content) {
                        "Annex C deviceRequest is required"
                    }
                    val encryptionInfo = requireNotNull(data["encryptionInfo"]?.jsonPrimitive?.content) {
                        "Annex C encryptionInfo is required"
                    }
                    val preview = wallet.previewAnnexCPresentation(
                        MobileWalletAnnexCRequest(
                            parsedRequest = wallet.parseAnnexCDeviceRequest(deviceRequest),
                            verifiedOrigin = input.request.verifiedOrigin,
                            selectedRegistryEntryIds = input.request.selectedRegistryEntryIds,
                            deviceRequestBase64Url = deviceRequest,
                            encryptionInfoBase64Url = encryptionInfo,
                        )
                    )
                    selectCredentials(preview.credentialOptions) { selections, selectedOptions ->
                        showAnnexCConsent(
                            wallet,
                            preview,
                            deviceRequest,
                            encryptionInfo,
                            selections,
                            selectedOptions,
                        )
                    }
                } else {
                    val preview = wallet.previewDigitalCredentialPresentation(input.request)
                    selectCredentials(preview.credentialOptions) { selections, selectedOptions ->
                        showConsent(wallet, preview, selections, selectedOptions)
                    }
                }
            }.onFailure {
                reportFailure(it)
            }
        }
    }

    private fun showAnnexCConsent(
        wallet: MobileWallet,
        preview: MobileWalletAnnexCPreview,
        deviceRequest: String,
        encryptionInfo: String,
        selections: List<MobileWalletPresentationCredentialSelection>,
        selectedOptions: List<MobileWalletPresentationCredentialOption>,
    ) {
        val trust = when (val value = preview.readerTrust) {
            is id.walt.wallet2.mobile.MobileWalletReaderTrust.Trusted -> "Trusted reader: ${value.certificateSubject}"
            is id.walt.wallet2.mobile.MobileWalletReaderTrust.Unverified -> "Unverified reader: ${value.reason}"
            id.walt.wallet2.mobile.MobileWalletReaderTrust.NotApplicable -> "Reader trust not applicable"
        }
        val claimLines = selectedOptions.flatMap { option ->
            option.disclosures.map { disclosure ->
                "${disclosure.name ?: disclosure.path}: ${disclosure.displayValue ?: disclosure.valueJson}"
            }
        }.distinct()
        val credentialLines = selectedOptions.map(::credentialTitle).distinct()
        AlertDialog.Builder(this)
            .setTitle("Share mobile document?")
            .setMessage(
                buildString {
                    append("Requester: ${preview.verifiedOrigin}\n")
                    append("$trust\n")
                    append("\nCredential${if (credentialLines.size == 1) "" else "s"}:\n${credentialLines.joinToString("\n")}\n")
                    if (claimLines.isNotEmpty()) append("\nData to share:\n${claimLines.joinToString("\n")}")
                }
            )
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                AndroidDigitalCredentialProvider.setCancellation(resultIntent)
                finishProviderResult()
            }
            .setPositiveButton("Share") { _, _ ->
                scope.launch {
                    runCatching {
                        wallet.submitAnnexCPresentation(
                            MobileWalletAnnexCSubmission(
                                requestId = preview.requestId,
                                verifiedOrigin = preview.verifiedOrigin,
                                deviceRequestBase64Url = deviceRequest,
                                encryptionInfoBase64Url = encryptionInfo,
                                selectedCredentialOptions = selections,
                            )
                        )
                    }.onSuccess { response ->
                        AndroidDigitalCredentialProvider.setResponse(resultIntent, response)
                        finishProviderResult()
                    }.onFailure {
                        reportFailure(it)
                    }
                }
            }
            .show()
    }

    private fun showConsent(
        wallet: MobileWallet,
        preview: MobileWalletDigitalCredentialPreview,
        selections: List<MobileWalletPresentationCredentialSelection>,
        selectedOptions: List<MobileWalletPresentationCredentialOption>,
    ) {
        val claimLines = selectedOptions.flatMap { option ->
            option.disclosures.filter { it.required || !it.selectable }.map { disclosure ->
                "${disclosure.name ?: disclosure.path}: ${disclosure.displayValue ?: disclosure.valueJson}"
            }
        }.distinct()
        val credentialLines = selectedOptions.map(::credentialTitle).distinct()
        val message = buildString {
            append("Requester: ${preview.request.verifierName ?: preview.verifiedOrigin}\n")
            append("Protocol: ${preview.protocol}\n")
            append("Response encryption: ${if (preview.encryption.isRequired) "required" else "not requested"}\n")
            append("\nCredential${if (credentialLines.size == 1) "" else "s"}:\n${credentialLines.joinToString("\n")}\n")
            if (claimLines.isNotEmpty()) append("\nData to share:\n${claimLines.joinToString("\n")}")
        }
        AlertDialog.Builder(this)
            .setTitle("Share digital credential?")
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                AndroidDigitalCredentialProvider.setCancellation(resultIntent)
                finishProviderResult()
            }
            .setPositiveButton("Share") { _, _ -> submitAfterConsent(wallet, preview, selections) }
            .show()
    }

    private fun submitAfterConsent(
        wallet: MobileWallet,
        preview: MobileWalletDigitalCredentialPreview,
        selections: List<MobileWalletPresentationCredentialSelection>,
    ) {
        scope.launch {
            runCatching {
                wallet.submitDigitalCredentialPresentation(
                    requestId = preview.requestId,
                    selectedCredentialOptions = selections,
                    selectedDisclosureOptions = emptyList(),
                )
            }.onSuccess { response ->
                AndroidDigitalCredentialProvider.setResponse(resultIntent, response)
                finishProviderResult()
            }.onFailure {
                reportFailure(it)
            }
        }
    }

    private fun selectCredentials(
        options: List<MobileWalletPresentationCredentialOption>,
        onSelected: (
            List<MobileWalletPresentationCredentialSelection>,
            List<MobileWalletPresentationCredentialOption>,
        ) -> Unit,
    ) {
        val groups = options.groupBy { it.queryId }.toSortedMap().toList()
        require(groups.isNotEmpty()) { "No credential satisfies this presentation request" }
        selectCredential(groups, 0, mutableListOf(), onSelected)
    }

    private fun selectCredential(
        groups: List<Pair<String, List<MobileWalletPresentationCredentialOption>>>,
        index: Int,
        selected: MutableList<MobileWalletPresentationCredentialOption>,
        onSelected: (
            List<MobileWalletPresentationCredentialSelection>,
            List<MobileWalletPresentationCredentialOption>,
        ) -> Unit,
    ) {
        if (index == groups.size) {
            onSelected(
                selected.map { MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId) },
                selected.toList(),
            )
            return
        }

        val options = groups[index].second
        require(options.isNotEmpty()) { "No credential satisfies one of the presentation queries" }
        if (options.size == 1) {
            selected += options.single()
            selectCredential(groups, index + 1, selected, onSelected)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Choose a credential")
            .setItems(options.map(::credentialTitle).toTypedArray()) { _, selectedIndex ->
                selected += options[selectedIndex]
                selectCredential(groups, index + 1, selected, onSelected)
            }
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                AndroidDigitalCredentialProvider.setCancellation(resultIntent)
                finishProviderResult()
            }
            .show()
    }

    private fun credentialTitle(option: MobileWalletPresentationCredentialOption): String =
        listOfNotNull(option.label, option.issuer, option.subject)
            .distinct()
            .joinToString(" · ")
            .ifEmpty { option.credentialId }

    private fun reportFailure(error: Throwable) {
        Log.e(TAG, "Digital credential presentation failed (${error::class.simpleName})")
        AndroidDigitalCredentialProvider.setFailure(resultIntent)
        finishProviderResult()
    }

    private fun finishProviderResult() {
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        private const val TAG = "WaltDigitalCredentials"
    }
}
