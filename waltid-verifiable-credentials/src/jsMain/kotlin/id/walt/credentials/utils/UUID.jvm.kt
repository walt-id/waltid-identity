package id.walt.credentials.utils

import uuid
@JsExport
actual fun randomUUID(): String = uuid.v4()
