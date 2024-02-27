import id.walt.issuer.base.config.OIDCIssuerServiceConfig
import id.walt.issuer.issuerModule
import id.walt.verifier.verifierModule
import id.walt.webwallet.db.Db
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.account.AuthenticationResult
import id.walt.webwallet.utils.WalletHttpClients
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.LoginRequestJson
import id.walt.webwallet.webWalletModule
import id.walt.webwallet.webWalletSetup
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import id.walt.issuer.base.config.ConfigManager as IssuerConfigManager
import id.walt.webwallet.config.ConfigManager as WalletConfigManager
import id.walt.webwallet.config.WebConfig as WalletWebConfig

class TestE2E {

    private fun ApplicationTestBuilder.newClient(token: String? = null) = createClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
        defaultRequest {
            contentType(ContentType.Application.Json)
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
        }
    }


    private fun setupTestWebWallet() {
        WalletConfigManager.preloadConfig("web", WalletWebConfig())

        webWalletSetup()
        WalletConfigManager.loadConfigs(emptyArray())

        Db.start()
    }

    private fun setupTestIssuer() {
        IssuerConfigManager.preloadConfig("issuer-service", OIDCIssuerServiceConfig("http://localhost"))

        IssuerConfigManager.loadConfigs(emptyArray())
    }

    @Test
    fun x() = testApplication {
        println("Running in ${Path(".").absolutePathString()}")

        var client = newClient()

        WalletHttpClients.defaultMethod = {
            newClient()
        }

        println("Setup web wallet...")
        setupTestWebWallet()

        println("Setup issuer...")
        setupTestIssuer()

        println("Starting application...")
        application {
            webWalletModule()
            issuerModule(withPlugins = false)
            verifierModule(withPlugins = false)
        }

        println("Running login...")
        val authResult = client.post("/wallet-api/auth/login") {
            setBody(LoginRequestJson.encodeToString(EmailAccountRequest(email = "user@email.com", password = "password") as AccountRequest))
        }.body<AuthenticationResult>()
        println("Login result: $authResult\n")

        client = newClient(authResult.token)

        println("Running wallet listing...")
        val walletListing = client.get("/wallet-api/wallet/accounts/wallets")
            .body<AccountWalletListing>()
        println("Wallet listing: $walletListing\n")

        val availableWallets = walletListing.wallets
        assertTrue { availableWallets.isNotEmpty() }
        val walletId = availableWallets.first().id

        println("Running DID listing...")
        val availableDids = client.get("/wallet-api/wallet/$walletId/dids")
            .body<List<WalletDid>>()
        println("DID listing: $availableDids\n")

        assertTrue { availableDids.isNotEmpty() }
        val did = availableDids.first().did

        // Issuer
        println("Calling issuer...")
        val issuanceUrl = client.post("/openid4vc/jwt/issue") {
            //language=JSON
            setBody(
                """
                {
                  "issuanceKey": {
                    "type": "local",
                    "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
                  },
                  "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
                  "vc": {
                    "@context": [
                      "https://www.w3.org/2018/credentials/v1",
                      "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                    ],
                    "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
                    "type": [
                      "VerifiableCredential",
                      "OpenBadgeCredential"
                    ],
                    "name": "JFF x vc-edu PlugFest 3 Interoperability",
                    "issuer": {
                      "type": [
                        "Profile"
                      ],
                      "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
                      "name": "Jobs for the Future (JFF)",
                      "url": "https://www.jff.org/",
                      "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                    },
                    "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                    "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
                    "credentialSubject": {
                      "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                      "type": [
                        "AchievementSubject"
                      ],
                      "achievement": {
                        "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                        "type": [
                          "Achievement"
                        ],
                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                        "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                        "criteria": {
                          "type": "Criteria",
                          "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                        },
                        "image": {
                          "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                          "type": "Image"
                        }
                      }
                    }
                  },
                  "mapping": {
                    "id": "<uuid>",
                    "issuer": {
                      "id": "<issuerDid>"
                    },
                    "credentialSubject": {
                      "id": "<subjectDid>"
                    },
                    "issuanceDate": "<timestamp>",
                    "expirationDate": "<timestamp-in:365d>"
                  }
                }
            """.trimIndent()
            )
        }.bodyAsText()
        println("Issuance URL: $issuanceUrl\n")

        // Wallet
        println("Claiming credential...")
        val result = client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
            parameter("did", did)

            contentType(ContentType.Text.Plain)
            setBody(issuanceUrl)
        }
        println("Claim result: $result")
        assertEquals(HttpStatusCode.OK, result.status)
    }
}
