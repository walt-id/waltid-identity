package id.walt.wallet2.handlers

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.wallet2.data.WalletKeyStoreEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Guards the token-type handling shared by [WalletIssuanceHandler] and
 * [WalletIssuanceSessionService].
 *
 * These decide whether a DPoP proof accompanies protected-resource requests, so a wrong answer
 * silently strips sender constraining rather than failing visibly.
 */
class DPoPSupportTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    private suspend fun entry(id: String, spec: KeySpec) = WalletKeyStoreEntry(
        keyId = id,
        legacyKey = null,
        crypto2Key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = spec,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        ),
    )

    private fun metadata(dpopAlgorithms: Set<String>?) = AuthorizationServerMetadata(
        issuer = "https://issuer.example/",
        // authorization_endpoint and response_types_supported are both required by the metadata's
        // own init block (RFC 8414 §2, plus a grant-type consistency check).
        authorizationEndpoint = "https://issuer.example/authorize",
        tokenEndpoint = "https://issuer.example/token",
        responseTypesSupported = setOf("code"),
        dpopSigningAlgValuesSupported = dpopAlgorithms,
    )

    @Test
    fun `a DPoP token uses the algorithms the server advertised`() {
        assertEquals(
            setOf("ES256"),
            dpopAlgorithmsForToken("DPoP", setOf("ES256")),
        )
    }

    @Test
    fun `token type matching is case insensitive`() {
        // RFC 6749 §7.1 registers token types case-insensitively, and servers vary in casing.
        assertEquals(setOf("ES256"), dpopAlgorithmsForToken("dpop", setOf("ES256")))
        assertNull(dpopAlgorithmsForToken("bearer", setOf("ES256")))
    }

    @Test
    fun `a Bearer token sends no DPoP proof even when the server advertises DPoP`() {
        // Advertising support does not mean the issued token is bound; only token_type decides.
        assertNull(dpopAlgorithmsForToken("Bearer", setOf("ES256")))
    }

    @Test
    fun `a DPoP token without advertised algorithms is rejected`() {
        // Self-contradictory server response: it cannot expect proofs it never described how to sign.
        assertFailsWith<IllegalArgumentException> {
            dpopAlgorithmsForToken("DPoP", null)
        }
    }

    @Test
    fun `an unrecognised token type is rejected rather than downgraded`() {
        // Treating an unknown type as Bearer would drop sender constraining without any signal.
        assertFailsWith<IllegalStateException> {
            dpopAlgorithmsForToken("mac", setOf("ES256"))
        }
    }

    @Test
    fun `authorization scheme follows the token type`() {
        assertEquals("DPoP", authorizationScheme("DPoP"))
        assertEquals("DPoP", authorizationScheme("dpop"))
        assertEquals("Bearer", authorizationScheme("Bearer"))
        // Absent token type means no DPoP context, so the plain Bearer scheme applies.
        assertEquals("Bearer", authorizationScheme(null))
    }

    @Test
    fun `DPoP is used when the key can sign an advertised algorithm`() = runTest {
        assertEquals(
            setOf("ES256"),
            usableDpopAlgorithms(metadata(setOf("ES256")), entry("p256", KeySpec.Ec(EcCurve.P256))),
        )
    }

    @Test
    fun `DPoP is skipped when the key cannot sign any advertised algorithm`() = runTest {
        // Regression guard: an Ed25519 did:key wallet against a server advertising only ES256 must
        // fall back to a Bearer token. Failing here previously broke every such issuance flow with
        // "No accepted JWS algorithm is supported by the key", because DPoP was engaged on
        // advertisement alone. DPoP is optional for the wallet.
        assertNull(
            usableDpopAlgorithms(
                metadata(setOf("ES256")),
                entry("ed25519", KeySpec.Edwards(EdwardsCurve.ED25519)),
            ),
        )
    }

    @Test
    fun `DPoP is skipped when the server advertises none`() = runTest {
        assertNull(usableDpopAlgorithms(metadata(null), entry("p256", KeySpec.Ec(EcCurve.P256))))
    }

    @Test
    fun `an empty advertised algorithm set is rejected by the metadata itself`() {
        // dpopSigningAlgorithms() also filters empty sets, but the model forbids constructing one,
        // so that filter is defence in depth rather than a reachable branch.
        assertFailsWith<IllegalArgumentException> { metadata(emptySet()) }
    }

    @Test
    fun `DPoP is skipped when the entry has no crypto2 key`() = runTest {
        // No signing representation means no proof can be built; fall back rather than fail.
        assertNull(
            usableDpopAlgorithms(
                metadata(setOf("ES256")),
                WalletKeyStoreEntry(keyId = "empty", legacyKey = null, crypto2Key = null),
            ),
        )
    }
}
