package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.Key
import id.walt.ebsi.EbsiEnvironment
import id.walt.oid4vc.data.dif.PresentationDefinition
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidEbsiCreateOptions(accreditationClientUri: String, taoIssuerUri: String,
                           ebsiEnvironment: EbsiEnvironment = EbsiEnvironment.conformance,
                           clientJwksUri: String = "$accreditationClientUri/jwks",
                           clientRedirectUri: String = "$accreditationClientUri/code-cb",
                           clientId: String = accreditationClientUri,
                           notBefore: Int? = null, notAfter: Int? = null, didRegistryApiVersion: Int = 4
) : DidCreateOptions(
    method = "ebsi",
    config = config(
        listOfNotNull(
          "accreditation_client_uri" to accreditationClientUri,
          "tao_issuer_uri" to taoIssuerUri,
          "client_jwks_uri" to clientJwksUri,
          "client_redirect_uris" to clientRedirectUri,
          "client_id" to clientId,
          ("not_before" to notBefore).takeIf { notBefore != null },
          ("not_after" to notAfter).takeIf { notAfter != null },
          ("ebsi_environment" to ebsiEnvironment),
          ("did_registry_api_version" to didRegistryApiVersion)
        ).associate { it.first to it.second!! }
    )
)
