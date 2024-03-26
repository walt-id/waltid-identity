package id.walt.webwallet.manifest.extractor

import id.walt.webwallet.manifest.provider.ManifestProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class EntraMockManifestExtractor : ManifestExtractor {

    override suspend fun extract(offerRequestUrl: String): JsonObject =
        Json.decodeFromString(
            """
        {
            "id": "133d7e92-d227-f74d-1a5b-354cbc8df49a",
            "display":
            {
                "locale": "en-US",
                "contract": "https://beta.eu.did.msidentity.com/v1.0/tenants/8bc955d9-38fd-4c15-a520-0c656407537a/verifiableCredentials/contracts/133d7e92-d227-f74d-1a5b-354cbc8df49a/manifest",
                "card":
                {
                    "title": "MyID",
                    "issuedBy": "walt.id",
                    "backgroundColor": "#000000",
                    "textColor": "#ffffff",
                    "logo":
                    {
                        "uri": "https://entra.walt.id/logo.png",
                        "description": "logo"
                    },
                    "description": "ID"
                },
                "consent":
                {
                    "title": "Consent",
                    "instructions": "Do consent"
                },
                "claims":
                {
                    "vc.credentialSubject.firstName":
                    {
                        "type": "String",
                        "label": "Name"
                    },
                    "vc.credentialSubject.lastName":
                    {
                        "type": "String",
                        "label": "Surname"
                    }
                },
                "id": "display"
            },
            "input":
            {
                "credentialIssuer": "https://beta.eu.did.msidentity.com/v1.0/tenants/8bc955d9-38fd-4c15-a520-0c656407537a/verifiableCredentials/issue",
                "issuer": "did:web:entra.walt.id",
                "attestations":
                {
                    "idTokens":
                    [
                        {
                            "id": "https://self-issued.me",
                            "encrypted": false,
                            "claims":
                            [
                                {
                                    "claim": "given_name",
                                    "required": false,
                                    "indexed": false
                                },
                                {
                                    "claim": "family_name",
                                    "required": false,
                                    "indexed": false
                                }
                            ],
                            "required": false,
                            "configuration": "https://self-issued.me",
                            "client_id": "",
                            "redirect_uri": ""
                        }
                    ]
                },
                "id": "input"
            },
            "iss": "did:web:entra.walt.id",
            "iat": 1704289683,
            "type": "${ManifestProvider.EntraManifestType}"
        }
        """.trimIndent()
        )

}
