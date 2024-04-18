package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.Key
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
class DidEbsiCreateOptions(version: Int,
                           authorisationToOnboard: JsonElement,
                           //vcSigningKey: Key,
                           nonce: String?,
                           notBefore: Instant? = null, notAfter: Instant? = null
) : DidCreateOptions(
    method = "ebsi",
    config = config(
        listOfNotNull(
            "version" to version,
            "authorisationToOnboard" to authorisationToOnboard,
            //"vcSigningKey" to vcSigningKey,
            "nonce" to nonce,
            ("notBefore" to notBefore).takeIf { notBefore != null },
            ("notAfter" to notAfter).takeIf { notAfter != null }
        ).associate { it.first to it.second!! }
    )
)
