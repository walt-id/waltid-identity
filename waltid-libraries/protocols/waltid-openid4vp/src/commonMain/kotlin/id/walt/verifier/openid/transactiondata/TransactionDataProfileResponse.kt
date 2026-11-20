package id.walt.verifier.openid.transactiondata

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject

/** Discovery projection. Schema information is read-only and never changes stored admission profiles. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TransactionDataProfileResponse(
    val type: String,
    val displayName: String,
    val fields: List<String>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadSchema: JsonObject? = null,
)

fun TransactionDataProfile.toResponse(): TransactionDataProfileResponse = TransactionDataProfileResponse(
    type, displayName, fields,
    payloadSchema = Ts12Payment.schema.takeIf { type == Ts12Payment.TYPE },
)
