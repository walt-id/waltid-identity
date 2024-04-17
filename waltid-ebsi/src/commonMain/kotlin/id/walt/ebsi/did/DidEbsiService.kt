package id.walt.ebsi.did

import id.walt.crypto.utils.MultiBaseUtils
import kotlin.random.Random

object DidEbsiService {

  /**
   * https://hub.ebsi.eu/vc-framework/did/legal-entities#generation-of-a-method-specific-identifier
   */
  fun generateRandomDid(): String {
    return MultiBaseUtils.encodeMultiBase58Btc(
      Random.nextBytes(17).also { it[0] = 1 }
    ).let {
      "did:ebsi:$it"
    }
  }


}