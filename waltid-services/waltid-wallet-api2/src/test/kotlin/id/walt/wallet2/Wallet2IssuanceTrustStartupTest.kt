package id.walt.wallet2

import id.walt.trust.model.TrustedEntityType
import id.walt.wallet2.OSSWallet2Service.toDomain
import id.walt.wallet2.trust.IssuerEntityTypeRule
import id.walt.wallet2.trust.WalletIssuanceTrust
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Wallet2IssuanceTrustStartupTest {

    @Test
    fun `issuance trust is off without a trust registry`() {
        assertNull(IssuanceTrustConfig().toDomain())
        assertNull(OSSWallet2ServiceConfig(publicBaseUrl = io.ktor.http.Url("http://localhost")).issuanceTrust.toDomain())
    }

    @Test
    fun `a trust registry turns issuance trust on with the PID and attestation provider defaults`() {
        val trust = assertNotNull(
            IssuanceTrustConfig(trustRegistry = IssuanceTrustRegistryConfig("https://trust.example.com/resolve/certificate-chain")).toDomain()
        )

        assertEquals(WalletIssuanceTrust.DEFAULT_CREDENTIAL_TYPES, trust.credentialTypes)
        assertEquals(setOf(TrustedEntityType.PID_PROVIDER), trust.entityTypesFor("urn:eudi:pid:1", null))
        assertEquals(setOf(TrustedEntityType.PID_PROVIDER), trust.entityTypesFor(null, "eu.europa.ec.eudi.pid.1"))
        assertEquals(setOf(TrustedEntityType.ATTESTATION_PROVIDER), trust.entityTypesFor("https://example.org/vct", null))
        assertFalse(trust.requireAuthenticatedSource)
    }

    @Test
    fun `configured rules, other roles and source authentication are applied`() {
        val trust = assertNotNull(
            IssuanceTrustConfig(
                trustRegistry = IssuanceTrustRegistryConfig(
                    resolveCertificateChainUrl = "https://trust.example.com/resolve/certificate-chain",
                    requireAuthenticatedSource = true,
                ),
                credentialTypes = listOf(
                    IssuerEntityTypeRuleConfig(doctypes = setOf("org.iso.18013.5.1.mDL"), entityTypes = setOf(TrustedEntityType.OTHER))
                ),
                otherCredentialTypes = emptySet(),
            ).toDomain()
        )

        assertEquals(
            listOf(IssuerEntityTypeRule(doctypes = setOf("org.iso.18013.5.1.mDL"), entityTypes = setOf(TrustedEntityType.OTHER))),
            trust.credentialTypes,
        )
        assertEquals(emptySet(), trust.entityTypesFor("urn:eudi:pid:1", null))
        assertTrue(trust.requireAuthenticatedSource)
    }

    @Test
    fun `registry headers are not printed`() {
        val config = IssuanceTrustRegistryConfig(
            resolveCertificateChainUrl = "https://trust.example.com/resolve/certificate-chain",
            headers = mapOf("Authorization" to "Bearer secret-token"),
        )

        assertFalse("secret-token" in config.toString())
        assertFalse("secret-token" in IssuanceTrustConfig(trustRegistry = config).toString())
        assertTrue("Authorization" in config.toString())
    }
}
