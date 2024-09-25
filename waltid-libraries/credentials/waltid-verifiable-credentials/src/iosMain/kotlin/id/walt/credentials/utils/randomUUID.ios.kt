package id.walt.credentials.utils

import platform.Foundation.NSUUID

actual fun randomUUID(): String= NSUUID.Uuid.random().UUIDString
