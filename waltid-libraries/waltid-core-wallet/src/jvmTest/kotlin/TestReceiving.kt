import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.helpers.WaltidServices
import id.walt.wallet.core.CoreWallet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TestReceiving {

    @Test
    fun testReceiveEntra() = runTest {
        WaltidServices.minimalInit()

        val key = JWKKey.generate(KeyType.secp256r1)
        val did = DidService.registerByKey("jwk", key).did

        val offer = "openid-vc://?request_uri=https://verifiedid.did.msidentity.com/v1.0/tenants/a8671fa1-780f-4af1-8341-cd431da2c46d/verifiableCredentials/issuanceRequests/d3afd1d9-6321-48a6-89bc-f004a5d17013"

        val result = CoreWallet.useOfferRequest(offer, did)

        println(result)
    }

}
