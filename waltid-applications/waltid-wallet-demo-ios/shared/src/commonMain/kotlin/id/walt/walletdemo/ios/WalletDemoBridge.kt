package id.walt.walletdemo.ios

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.client.EnterpriseWalletServiceClient
import id.walt.wallet2.client.WalletClient
import id.walt.wallet2.client.WalletClientAttestationProvider
import id.walt.wallet2.client.WalletClientAttestationStatus
import id.walt.wallet2.client.WalletClientEnvironment
import id.walt.wallet2.client.WalletClientEnvironmentProfile
import id.walt.wallet2.client.WalletEndpointRewriter
import id.walt.wallet2.client.toEnvironment
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun quickstartEnterpriseBaseUrl(): String =
    WalletClientEnvironmentProfile.QuickstartLocal.toEnvironment().enterpriseBaseUrl

fun quickstartWalletPath(): String =
    WalletClientEnvironmentProfile.QuickstartLocal.toEnvironment().walletPath

fun quickstartHostHeader(): String =
    WalletClientEnvironmentProfile.QuickstartLocal.toEnvironment().enterpriseHostHeader

fun quickstartAttesterServiceRef(): String =
    WalletClientEnvironmentProfile.QuickstartLocal.toEnvironment().attesterServiceRef

fun quickstartInstanceKeyReference(): String =
    WalletClientEnvironmentProfile.QuickstartLocal.toEnvironment().instanceKeyReference

data class BridgeBootstrapResult(
    val keyId: String,
    val did: String,
)

data class BridgeCredential(
    val id: String,
    val format: String,
    val issuer: String,
    val label: String,
    val addedAt: String,
)

data class BridgePresentResult(
    val success: Boolean,
    val redirectTo: String,
)

data class BridgeOperationResult(
    val success: Boolean,
    val message: String,
    val credentialCount: Int = 0,
)

class WalletDemoBridgeController {
    private val adapter = IosMobileWalletAdapter()
    private var environment = WalletClientEnvironmentProfile.QuickstartLocal.toEnvironment()
    private var requireAttestation = false

    private fun walletClient(): WalletClient {
        val enterpriseClient = EnterpriseWalletServiceClient(environment = environment)
        val attestationProvider = WalletClientAttestationProvider {
            if (environment.enterpriseBaseUrl.isBlank() || environment.walletPath.isBlank()) {
                return@WalletClientAttestationProvider WalletClientAttestationStatus.NotRequired
            }
            val attestation = enterpriseClient.getCurrentAttestation()
            WalletClientAttestationStatus.Present(attestation.expiresAt)
        }
        return WalletClient(
            adapter = adapter,
            endpointRewriter = WalletEndpointRewriter.Noop,
            attestationProvider = attestationProvider,
        )
    }

    private fun enterpriseClient(): EnterpriseWalletServiceClient =
        EnterpriseWalletServiceClient(environment = environment)

    fun updateEnvironment(
        baseUrl: String,
        walletPath: String,
        hostHeader: String,
        bearerToken: String,
        attesterRef: String,
        keyRef: String,
    ) {
        environment = WalletClientEnvironment(
            enterpriseBaseUrl = baseUrl,
            enterpriseHostHeader = hostHeader,
            bearerToken = bearerToken,
            walletPath = walletPath,
            attesterServiceRef = attesterRef,
            instanceKeyReference = keyRef,
        )
    }

    fun applyProfile(profileName: String) {
        val profile = WalletClientEnvironmentProfile.entries.firstOrNull { it.name == profileName }
            ?: return
        if (profile != WalletClientEnvironmentProfile.Custom) {
            environment = profile.toEnvironment()
        }
    }

    fun setRequireAttestation(value: Boolean) {
        requireAttestation = value
    }

    suspend fun bootstrap(): BridgeBootstrapResult {
        val result = walletClient().bootstrapWallet(
            keyType = KeyType.secp256r1,
            didMethod = "key",
        )
        return BridgeBootstrapResult(keyId = result.keyId, did = result.did)
    }

    suspend fun receiveCredential(offerUrl: String, txCode: String?): BridgeOperationResult {
        val ids = walletClient().receiveCredential(
            offerUrl = offerUrl,
            txCode = txCode,
            requireAttestation = requireAttestation,
        )
        return BridgeOperationResult(
            success = true,
            message = "Received ${ids.size} credential(s)",
            credentialCount = ids.size,
        )
    }

    suspend fun enterpriseReceive(offerUrl: String): BridgeOperationResult {
        val response = enterpriseClient().receivePreAuthorized(
            offerUrl = offerUrl,
            keyReference = environment.instanceKeyReference.trim(),
        )
        return BridgeOperationResult(
            success = true,
            message = "Enterprise received credential(s)",
        )
    }

    suspend fun listCredentials(): List<BridgeCredential> =
        walletClient().listCredentials().map { summary ->
            BridgeCredential(
                id = summary.id,
                format = summary.format,
                issuer = summary.issuer ?: "Unknown",
                label = summary.label ?: summary.format,
                addedAt = summary.addedAt ?: "",
            )
        }

    suspend fun presentCredential(requestUrl: String): BridgePresentResult {
        val result = walletClient().presentCredential(requestUrl = requestUrl)
        return BridgePresentResult(
            success = result.transmissionSuccess ?: false,
            redirectTo = result.redirectTo ?: "",
        )
    }

    suspend fun enterprisePresent(requestUrl: String): BridgeOperationResult {
        enterpriseClient().present(
            requestUrl = requestUrl,
            keyReference = environment.instanceKeyReference.trim(),
        )
        return BridgeOperationResult(success = true, message = "Enterprise present successful")
    }

    suspend fun obtainAttestation(): BridgeOperationResult {
        val request = buildJsonObject {
            put("clientAttesterServiceRef", environment.attesterServiceRef.trim())
            put("instanceKeyReference", environment.instanceKeyReference.trim())
        }
        val response = enterpriseClient().obtainAttestation(request)
        return BridgeOperationResult(
            success = true,
            message = "Attestation obtained (expiresAt=${response.expiresAt})",
        )
    }

    suspend fun checkAttestation(): BridgeOperationResult {
        val att = enterpriseClient().getCurrentAttestation()
        return BridgeOperationResult(
            success = true,
            message = "Attestation present (expiresAt=${att.expiresAt})",
        )
    }
}
