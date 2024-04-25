package id.walt.ebsi.did

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DidEbsiBaseDocument(
  @EncodeDefault @SerialName("@context")  val context: List<String> = DEFAULT_CONTEXT
) {
  companion object {
    val DEFAULT_CONTEXT =
      listOf("https://www.w3.org/ns/did/v1", "https://w3id.org/security/suites/jws-2020/v1")
  }
}
