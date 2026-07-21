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
                    showAnnexCConsent(wallet, preview, deviceRequest, encryptionInfo)
                } else {
                    val preview = wallet.previewDigitalCredentialPresentation(input.request)
                    showConsent(wallet, preview)
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
    ) {
        val trust = when (val value = preview.readerTrust) {
            is id.walt.wallet2.mobile.MobileWalletReaderTrust.Trusted -> "Trusted reader: ${value.certificateSubject}"
            is id.walt.wallet2.mobile.MobileWalletReaderTrust.Unverified -> "Unverified reader: ${value.reason}"
            id.walt.wallet2.mobile.MobileWalletReaderTrust.NotApplicable -> "Reader trust not applicable"
        }
        val claimLines = preview.credentialOptions.flatMap { option ->
            option.disclosures.map { disclosure ->
                "${disclosure.name ?: disclosure.path}: ${disclosure.displayValue ?: disclosure.valueJson}"
            }
        }.distinct()
        AlertDialog.Builder(this)
            .setTitle("Share mobile document?")
            .setMessage(
                buildString {
                    append("Requester: ${preview.verifiedOrigin}\n")
                    append("$trust\n")
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
                        val selections = preview.credentialOptions
                            .groupBy { it.queryId }
                            .map { (queryId, options) ->
                                MobileWalletPresentationCredentialSelection(queryId, options.first().credentialId)
                            }
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

    private fun showConsent(wallet: MobileWallet, preview: MobileWalletDigitalCredentialPreview) {
        val claimLines = preview.credentialOptions.flatMap { option ->
            option.disclosures.map { disclosure ->
                "${disclosure.name ?: disclosure.path}: ${disclosure.displayValue ?: disclosure.valueJson}"
            }
        }.distinct()
        val message = buildString {
            append("Requester: ${preview.request.verifierName ?: preview.verifiedOrigin}\n")
            append("Protocol: ${preview.protocol}\n")
            append("Response encryption: ${if (preview.encryption.isRequired) "required" else "not requested"}\n")
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
            .setPositiveButton("Share") { _, _ -> submitAfterConsent(wallet, preview) }
            .show()
    }

    private fun submitAfterConsent(wallet: MobileWallet, preview: MobileWalletDigitalCredentialPreview) {
        scope.launch {
            runCatching {
                val selections = preview.credentialOptions
                    .groupBy { it.queryId }
                    .map { (queryId, options) ->
                        MobileWalletPresentationCredentialSelection(queryId, options.first().credentialId)
                    }
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

    private fun reportFailure(error: Throwable) {
        Log.e(TAG, "Digital credential presentation failed (${error::class.simpleName}): ${error.message}")
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
