package id.walt.credentials.representations

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class X5CCertificateString(val base64Der: String)

@Serializable
@JvmInline
value class X5CList(val x5c: List<X5CCertificateString>)
