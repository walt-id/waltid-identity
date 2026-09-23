package id.walt.certificate.x509.nodejs

import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

@JsModule("crypto")
@JsNonModule
external object NodejsCrypto {
    fun createPublicKey(key: String): NodejsPublicKey
    fun createPrivateKey(key: String): NodejsPrivateKey
    fun getRandomValues(array: Int8Array): Int8Array
}

external interface NodejsKeyObject {
    val type: String
}

external interface NodejsPublicKey : NodejsKeyObject
external interface NodejsPrivateKey : NodejsKeyObject
