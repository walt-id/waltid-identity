package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.pkcs11.Pkcs11KeyProvider
import id.walt.crypto2.pkcs11.Pkcs11Options
import id.walt.crypto2.pkcs11.Pkcs11Pin
import id.walt.crypto2.pkcs11.Pkcs11PinResolver
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

data class Pkcs11SoftHsmResult(
    val providerId: String,
    val providerMetadataBytes: Int,
    val pinReferencePresent: Boolean,
    val pinValuePresent: Boolean,
    val initialVerified: Boolean,
    val restartVerified: Boolean,
    val deleted: Boolean,
    val restoreRejectedAfterDelete: Boolean,
)

internal data class Pkcs11SoftHsmConfiguration(
    val libraryPath: String,
    val libraryAutoDetected: Boolean,
    val slotListIndex: Int,
    val pinValue: String,
    val pinReference: String,
)

internal fun resolvePkcs11SoftHsmConfiguration(
    environment: Map<String, String>,
    libraryCandidates: List<String> = COMMON_SOFTHSM_LIBRARY_PATHS,
    isRegularFile: (String) -> Boolean = { Files.isRegularFile(Path.of(it)) },
): Pkcs11SoftHsmConfiguration {
    val libraryOverride = environment[Pkcs11SoftHsmExample.LIBRARY_ENV]?.takeIf(String::isNotBlank)
    val libraryPath = libraryOverride ?: libraryCandidates.firstOrNull(isRegularFile)
        ?: error(
            "SoftHSM library not found. Install softhsm2/softhsm or set ${Pkcs11SoftHsmExample.LIBRARY_ENV}.",
        )
    val slotListIndex = environment[Pkcs11SoftHsmExample.SLOT_ENV]?.let { configured ->
        configured.toIntOrNull() ?: error("${Pkcs11SoftHsmExample.SLOT_ENV} must be an integer")
    } ?: 0
    val pinValue = environment[Pkcs11SoftHsmExample.PIN_ENV]?.takeIf(String::isNotBlank)
        ?: error("Required environment variable is missing: ${Pkcs11SoftHsmExample.PIN_ENV}")
    val pinReference = environment[Pkcs11SoftHsmExample.PIN_REFERENCE_ENV]
        ?.takeIf(String::isNotBlank)
        ?: "env:${Pkcs11SoftHsmExample.PIN_ENV}"
    require(pinValue !in pinReference) { "PKCS11 PIN reference must not contain the PIN value" }
    return Pkcs11SoftHsmConfiguration(
        libraryPath = libraryPath,
        libraryAutoDetected = libraryOverride == null,
        slotListIndex = slotListIndex,
        pinValue = pinValue,
        pinReference = pinReference,
    )
}

object Pkcs11SoftHsmExample {
    suspend fun run(
        output: ExampleOutput,
        environment: Map<String, String> = System.getenv(),
    ): Pkcs11SoftHsmResult {
        output("=== PKCS11 SoftHSM managed key lifecycle ===")
        val configuration = resolvePkcs11SoftHsmConfiguration(environment)
        val librarySource = if (configuration.libraryAutoDetected) "auto-detected" else LIBRARY_ENV
        output("1. Read SoftHSM library=${configuration.libraryPath} ($librarySource), slotListIndex=${configuration.slotListIndex}")
        val pinValue = configuration.pinValue
        val pinReference = configuration.pinReference
        output("   Resolve PIN from $PIN_ENV via pinReference=$pinReference; PIN value is hidden")

        // The resolver maps a persisted reference to secret material supplied only at operation time.
        val pinResolver = Pkcs11PinResolver { requestedReference ->
            require(requestedReference == pinReference) { "Unexpected PKCS11 PIN reference" }
            Pkcs11Pin(pinValue.toCharArray())
        }
        val provider = Pkcs11KeyProvider(pinResolver = pinResolver)
        val initialRuntime = CryptoRuntime(
            softwareProviders = emptyList(),
            managedProviders = listOf(provider),
        )
        output("2. Construct Pkcs11PinResolver, provider=${provider.id.value}, and managed CryptoRuntime")

        val alias = "crypto2-example-${UUID.randomUUID()}"
        // Provider options persist the reference and unique alias, never the resolved PIN.
        val options = Pkcs11Options(
            libraryPath = configuration.libraryPath,
            slotListIndex = configuration.slotListIndex,
            pinReference = pinReference,
            alias = alias,
        )
        output(
            "3. Create Pkcs11Options: slotListIndex=${configuration.slotListIndex}, alias=$alias, " +
                "pinReference=$pinReference",
        )

        var generated: ManagedKey? = null
        var restartedRuntime: CryptoRuntime? = null
        var deleted = false
        try {
            generated = initialRuntime.generateManagedKey(
                provider = Pkcs11KeyProvider.ID,
                request = GenerateManagedKeyRequest(
                    id = KeyId(alias),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                    providerOptions = options.encode(),
                ),
            )
            val stored = generated.storedKey
            output("4. Generated managed key: id=${stored.id.value}, spec=P-256, usages=SIGN|VERIFY")
            output(
                "   Safe StoredKey metadata: provider=${stored.provider.value}, schema=${stored.providerSchemaVersion}, " +
                    "providerDataBytes=${stored.providerData.size}, publicEncoding=${stored.publicKey?.encodingFormat}",
            )

            // PKCS11 providerData is JSON containing options and alias; inspect it without printing the document.
            val providerMetadataJson = stored.providerData.toByteArray().decodeToString()
            Json.parseToJsonElement(providerMetadataJson)
            val pinReferencePresent = pinReference in providerMetadataJson
            val pinValuePresent = pinValue in providerMetadataJson
            check(pinReferencePresent && !pinValuePresent) { "Serialized PKCS11 metadata must contain only the PIN reference" }
            output("5. Serialization safety: pinReferencePresent=$pinReferencePresent, pinValuePresent=$pinValuePresent")

            val algorithm = SignatureAlgorithm.Ecdsa(digest = DigestAlgorithm.SHA_256)
            val message = "PKCS11 SoftHSM example".encodeToByteArray()
            val initialSignature = requireNotNull(generated.capabilities.signer).sign(
                data = message,
                algorithm = algorithm,
            )
            val initialVerified = requireNotNull(generated.capabilities.verifier).verify(
                data = message,
                signature = initialSignature,
                algorithm = algorithm,
            )
            output("6. Initial sign/verify: signatureEncoding=P1363, verified=$initialVerified")

            // A managed key serializes directly to its provider-neutral StoredKey representation.
            val serializedKey = Json.encodeToString(generated)
            check(pinValue !in serializedKey) { "Serialized managed key must not contain the PIN" }
            val decodedKey = Json.decodeFromString<ManagedKey>(serializedKey)
            val restartedProvider = Pkcs11KeyProvider(pinResolver = pinResolver)
            restartedRuntime = CryptoRuntime(
                softwareProviders = emptyList(),
                managedProviders = listOf(restartedProvider),
            )
            val restored = restartedRuntime.restore(decodedKey)
            val restartSignature = requireNotNull(restored.capabilities.signer).sign(
                data = message,
                algorithm = algorithm,
            )
            val restartVerified = requireNotNull(restored.capabilities.verifier).verify(
                data = message,
                signature = restartSignature,
                algorithm = algorithm,
            )
            output("7. New runtime restore + sign/verify: restoredId=${restored.id.value}, verified=$restartVerified")

            val deletion = requireNotNull(restored.capabilities.deleter).delete()
            deleted = deletion == KeyDeletionResult.Deleted
            output("8. Delete token key: deleted=$deleted")
            val restoreRejectedAfterDelete = try {
                restartedRuntime.restore(decodedKey)
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            output("9. Restore after deletion: rejected=$restoreRejectedAfterDelete")

            check(initialVerified && restartVerified && deleted && restoreRejectedAfterDelete)
            return Pkcs11SoftHsmResult(
                providerId = provider.id.value,
                providerMetadataBytes = stored.providerData.size,
                pinReferencePresent = pinReferencePresent,
                pinValuePresent = pinValuePresent,
                initialVerified = initialVerified,
                restartVerified = restartVerified,
                deleted = deleted,
                restoreRejectedAfterDelete = restoreRejectedAfterDelete,
            )
        } finally {
            // A unique alias plus unconditional cleanup keeps repeated presentations isolated.
            if (!deleted) {
                generated?.capabilities?.deleter?.delete()
            }
            restartedRuntime?.close()
            initialRuntime.close()
        }
    }

    const val LIBRARY_ENV = "WALTID_SOFTHSM2_LIBRARY"
    const val SLOT_ENV = "WALTID_SOFTHSM2_SLOT_INDEX"
    const val PIN_ENV = "WALTID_SOFTHSM2_PIN"
    const val PIN_REFERENCE_ENV = "WALTID_SOFTHSM2_PIN_REFERENCE"
}

internal val COMMON_SOFTHSM_LIBRARY_PATHS = listOf(
    "/usr/lib/softhsm/libsofthsm2.so",
    "/usr/lib/pkcs11/libsofthsm2.so",
    "/usr/lib64/pkcs11/libsofthsm2.so",
    "/usr/local/lib/softhsm/libsofthsm2.so",
    "/usr/local/lib/softhsm/libsofthsm2.dylib",
    "/opt/homebrew/lib/softhsm/libsofthsm2.so",
    "/opt/homebrew/lib/softhsm/libsofthsm2.dylib",
)

suspend fun main() {
    runExampleCommand("pkcs11-softhsm", jvmExampleCommands)
}
