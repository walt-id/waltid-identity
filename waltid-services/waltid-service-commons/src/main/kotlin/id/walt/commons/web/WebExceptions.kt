@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.commons.web

import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
open class WebException(val status: Int, override val message: String) : Exception(message)

@Serializable
sealed class SerializableWebException(val status: Int, override val message: String?) : Exception(message)

@Serializable
@SerialName("DuplicateTarget")
data class DuplicateTargetException(
    @SerialName("duplicate_info")
    val duplicateInfo: String? = null,

    val target: String? = null,
    @SerialName("target_entry_type")
    val targetEntryType: String? = null
) : SerializableWebException(
    status = HttpStatusCode.Conflict.value,
    message = "Duplicate target for this operation - you are trying to save to resources at the same path. Overwriting targets like this is not allowed. The old resource at the target must be removed first."
)

class UnauthorizedException(
    message: String
) : WebException(HttpStatusCode.Unauthorized.value, message)

open class ConflictException(
    override val message: String
) : WebException(HttpStatusCode.Conflict.value, message)

class ForbiddenException(
    message: String
) : WebException(HttpStatusCode.Forbidden.value, message)

class UnsupportedMediaTypeException(
    message: String
) : WebException(HttpStatusCode.UnsupportedMediaType.value, message)
