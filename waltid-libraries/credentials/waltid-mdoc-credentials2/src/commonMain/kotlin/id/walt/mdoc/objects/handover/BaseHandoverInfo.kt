@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.handover

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborArray

@Serializable
@CborArray
sealed interface BaseHandoverInfo
