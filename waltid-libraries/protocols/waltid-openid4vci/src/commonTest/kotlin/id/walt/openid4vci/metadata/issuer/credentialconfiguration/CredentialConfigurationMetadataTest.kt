package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ClaimDescription
import id.walt.openid4vci.metadata.issuer.ClaimDisplay
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialDisplayBackgroundImage
import id.walt.openid4vci.metadata.issuer.CredentialDisplayLogo
import id.walt.openid4vci.metadata.issuer.CredentialMetadata
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.openid4vci.metadata.issuer.ProofType
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialConfigurationMetadataTest {

    @Test
    fun `key attestation requirements must not contain empty values`() {
        assertFailsWith<IllegalArgumentException> {
            ProofType(
                proofSigningAlgValuesSupported = setOf("ES256"),
                keyAttestationsRequired = KeyAttestationsRequired(keyStorage = emptySet()),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProofType(
                proofSigningAlgValuesSupported = setOf("ES256"),
                keyAttestationsRequired = KeyAttestationsRequired(userAuthentication = emptySet()),
            )
        }
    }

    @Test
    fun `key attestation requirements can be empty`() {
        ProofType(
            proofSigningAlgValuesSupported = setOf("ES256"),
            keyAttestationsRequired = KeyAttestationsRequired(),
        )
    }

    @Test
    fun `credential metadata display must not be empty when present`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(display = emptyList()),
            )
        }
    }

    @Test
    fun `credential metadata display locales must be unique`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(name = "Credential", locale = "en"),
                        CredentialDisplay(name = "Credential 2", locale = "en"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `credential metadata display locale must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(name = "Credential", locale = " "),
                    ),
                ),
            )
        }
    }

    @Test
    fun `credential metadata display logo uri must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(
                            name = "Credential",
                            logo = CredentialDisplayLogo(uri = " "),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `credential metadata display logo uri must include a scheme`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(
                            name = "Credential",
                            logo = CredentialDisplayLogo(uri = "logo.png"),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `credential metadata display background image uri must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(
                            name = "Credential",
                            backgroundImage = CredentialDisplayBackgroundImage(uri = " "),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `credential metadata display background image uri must include a scheme`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(
                            name = "Credential",
                            backgroundImage = CredentialDisplayBackgroundImage(uri = "bg.png"),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `credential metadata claims must not be empty when present`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(claims = emptyList()),
            )
        }
    }

    @Test
    fun `credential metadata accepts claims when non-empty`() {
        CredentialConfiguration(
            id = "cred-id-1",
            format = CredentialFormat.SD_JWT_VC,
            credentialMetadata = CredentialMetadata(
                claims = listOf(
                    ClaimDescription(path = listOf("credentialSubject", "given_name"))
                ),
            ),
        )
    }

    @Test
    fun `claim description path must not be empty`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimDescription(path = emptyList())
        }
    }

    @Test
    fun `claim description path must not contain blank segments`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimDescription(path = listOf("credentialSubject", " "))
        }
    }

    @Test
    fun `claim display must not be empty when present`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimDescription(
                path = listOf("credentialSubject", "given_name"),
                display = emptyList(),
            )
        }
    }

    @Test
    fun `claim display locales must be unique`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimDescription(
                path = listOf("credentialSubject", "given_name"),
                display = listOf(
                    ClaimDisplay(name = "Given Name", locale = "en"),
                    ClaimDisplay(name = "Given Name 2", locale = "en"),
                ),
            )
        }
    }

    @Test
    fun `claim display name must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimDescription(
                path = listOf("credentialSubject", "given_name"),
                display = listOf(
                    ClaimDisplay(name = " "),
                ),
            )
        }
    }

    @Test
    fun `claim display locale must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimDescription(
                path = listOf("credentialSubject", "given_name"),
                display = listOf(
                    ClaimDisplay(name = "Given Name", locale = " "),
                ),
            )
        }
    }

    @Test
    fun `accepts full credential metadata example`() {
        CredentialConfiguration(
            id = "cred-id-1",
            format = CredentialFormat.SD_JWT_VC,
            credentialMetadata = CredentialMetadata(
                display = listOf(
                    CredentialDisplay(
                        name = "IdentityCredential",
                        locale = "en-US",
                        logo = CredentialDisplayLogo(
                            uri = "https://university.example.edu/public/logo.png",
                            altText = "a square logo of a university",
                        ),
                        backgroundColor = "#12107c",
                        textColor = "#FFFFFF",
                    )
                ),
                claims = listOf(
                    ClaimDescription(
                        path = listOf("given_name"),
                        display = listOf(
                            ClaimDisplay(name = "Given Name", locale = "en-US"),
                            ClaimDisplay(name = "Vorname", locale = "de-DE"),
                        ),
                    ),
                    ClaimDescription(
                        path = listOf("family_name"),
                        display = listOf(
                            ClaimDisplay(name = "Surname", locale = "en-US"),
                            ClaimDisplay(name = "Nachname", locale = "de-DE"),
                        ),
                    ),
                    ClaimDescription(path = listOf("email")),
                    ClaimDescription(path = listOf("phone_number")),
                    ClaimDescription(
                        path = listOf("address"),
                        display = listOf(
                            ClaimDisplay(name = "Place of residence", locale = "en-US"),
                            ClaimDisplay(name = "Wohnsitz", locale = "de-DE"),
                        ),
                    ),
                    ClaimDescription(path = listOf("address", "street_address")),
                    ClaimDescription(path = listOf("address", "locality")),
                    ClaimDescription(path = listOf("address", "region")),
                    ClaimDescription(path = listOf("address", "country")),
                    ClaimDescription(path = listOf("birthdate")),
                    ClaimDescription(path = listOf("is_over_18")),
                    ClaimDescription(path = listOf("is_over_21")),
                    ClaimDescription(path = listOf("is_over_65")),
                ),
            ),
        )
    }
}
