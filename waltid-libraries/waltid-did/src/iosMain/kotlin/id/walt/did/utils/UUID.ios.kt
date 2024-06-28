package id.walt.did.utils

import platform.Foundation.NSUUID

actual fun randomUUID(): String= NSUUID.UUID().UUIDString