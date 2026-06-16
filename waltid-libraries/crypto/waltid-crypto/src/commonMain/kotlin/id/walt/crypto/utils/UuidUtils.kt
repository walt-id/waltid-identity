package id.walt.crypto.utils

import kotlin.uuid.Uuid

object UuidUtils {
    fun randomUUID(): Uuid = Uuid.random()

    fun randomUUIDString(): String = Uuid.random().toString()
}