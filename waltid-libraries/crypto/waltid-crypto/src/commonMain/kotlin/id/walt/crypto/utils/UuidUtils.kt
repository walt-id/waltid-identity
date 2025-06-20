package id.walt.crypto.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object UuidUtils {
    fun randomUUID(): Uuid = Uuid.random()

    fun randomUUIDString(): String = Uuid.random().toString()
}