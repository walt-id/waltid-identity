package id.walt.ades.jades

import id.walt.ades.validation.ValidationData
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builders and readers for the generic JAdES syntax (pkiOb, tstContainer, xVals, rVals, validationVals). */
internal object JadesComponents {

    fun pkiOb(der: ByteArray): JsonObject = buildJsonObject { put("val", JadesEncoding.b64(der)) }

    fun tstContainer(tokens: List<ByteArray>, canonAlg: String? = null): JsonObject = buildJsonObject {
        canonAlg?.let { put("canonAlg", it) }
        put("tstTokens", JsonArray(tokens.map { pkiOb(it) }))
    }

    fun xVals(certificates: List<X509Certificate>): JsonArray =
        JsonArray(certificates.map { buildJsonObject { put("x509Cert", pkiOb(it.encoded)) } })

    fun rVals(crls: List<X509Crl>, ocsps: List<OcspResponse>): JsonObject = buildJsonObject {
        if (crls.isNotEmpty()) put("crlVals", JsonArray(crls.map { pkiOb(it.encoded) }))
        if (ocsps.isNotEmpty()) put("ocspVals", JsonArray(ocsps.map { pkiOb(it.encoded) }))
    }

    /** validationVals type (tstVD, anyValData); null when [data] is empty. */
    fun validationVals(data: ValidationData): JsonObject? = if (data.isEmpty) null else buildJsonObject {
        if (data.certificates.isNotEmpty()) put(JadesHeaders.X_VALS, xVals(data.certificates))
        if (data.crls.isNotEmpty() || data.ocsps.isNotEmpty()) put(JadesHeaders.R_VALS, rVals(data.crls, data.ocsps))
    }

    class TstContainer(val canonAlg: String?, val tokens: List<ByteArray?>, val problems: List<String>)

    fun readTstContainer(value: JsonElement): TstContainer {
        val obj = value as? JsonObject ?: return TstContainer(null, emptyList(), listOf("time-stamp container is not a JSON object"))
        val problems = mutableListOf<String>()
        val tokens = obj.array("tstTokens")?.map { t ->
            val token = t as? JsonObject
            if (token?.string("type") != null || token?.string("encoding") != null) problems += "only RFC 3161 time-stamp tokens are supported in JAdES baseline signatures"
            token?.string("val")?.let { runCatching { JadesEncoding.fromAnyBase64(it) }.getOrNull() }
        } ?: emptyList<ByteArray?>().also { problems += "tstTokens missing" }
        if (tokens.isEmpty()) problems += "tstTokens must not be empty"
        return TstContainer(obj.string("canonAlg"), tokens, problems)
    }

    private fun pkiObBytes(element: JsonElement?): ByteArray? =
        (element as? JsonObject)?.string("val")?.let { runCatching { JadesEncoding.fromAnyBase64(it) }.getOrNull() }

    fun readCertificates(value: JsonElement?, problems: MutableList<String>): List<X509Certificate> =
        (value as? JsonArray)?.mapNotNull { item ->
            val bytes = pkiObBytes((item as? JsonObject)?.get("x509Cert")) ?: return@mapNotNull null
            runCatching { X509Certificate(bytes) }.onFailure { problems += "invalid certificate in xVals: ${it.message}" }.getOrNull()
        } ?: emptyList()

    fun readRevocation(value: JsonElement?, into: ValidationData, problems: MutableList<String>) {
        val obj = value as? JsonObject ?: return
        obj.array("crlVals")?.forEach { item ->
            pkiObBytes(item)?.let { b -> runCatching { X509Crl(b) }.onFailure { problems += "invalid CRL in rVals: ${it.message}" }.getOrNull()?.let(into::add) }
        }
        obj.array("ocspVals")?.forEach { item ->
            pkiObBytes(item)?.let { b -> runCatching { OcspResponse(b) }.onFailure { problems += "invalid OCSP response in rVals: ${it.message}" }.getOrNull()?.let(into::add) }
        }
    }

    fun readValidationVals(value: JsonElement?, into: ValidationData, problems: MutableList<String>) {
        val obj = value as? JsonObject ?: return
        readCertificates(obj[JadesHeaders.X_VALS], problems).forEach(into::add)
        readRevocation(obj[JadesHeaders.R_VALS], into, problems)
    }

    fun certificateChain(header: JsonObject, problems: MutableList<String>): List<X509Certificate> =
        header.array(JadesHeaders.X5C)?.mapNotNull { e ->
            (e as? JsonPrimitive)?.content?.let { s ->
                runCatching { X509Certificate(JadesEncoding.fromAnyBase64(s)) }.onFailure { problems += "invalid x5c certificate: ${it.message}" }.getOrNull()
            }
        } ?: emptyList()

    /** All validation material embedded in a signature (x5c, xVals, rVals, tstVD, anyValData, axVals, arVals). */
    fun embeddedValidationData(signature: JadesSignature, problems: MutableList<String> = mutableListOf()): ValidationData {
        val data = ValidationData()
        certificateChain(signature.protectedHeader, problems).forEach(data::add)
        for (c in signature.etsiU) when (c.name) {
            JadesHeaders.X_VALS, JadesHeaders.AX_VALS -> readCertificates(c.value, problems).forEach(data::add)
            JadesHeaders.R_VALS, JadesHeaders.AR_VALS -> readRevocation(c.value, data, problems)
            JadesHeaders.TST_VD, JadesHeaders.ANY_VAL_DATA -> readValidationVals(c.value, data, problems)
        }
        return data
    }

    /** DER tokens of all time-stamp containers (adoTst, sigTst, arcTst). */
    fun allTimestampTokens(signature: JadesSignature): List<ByteArray> {
        val containers = listOfNotNull(signature.protectedHeader[JadesHeaders.ADO_TST]) +
            signature.etsiU.filter { it.name == JadesHeaders.SIG_TST || it.name == JadesHeaders.ARC_TST }.map { it.value }
        return containers.flatMap { readTstContainer(it).tokens.filterNotNull() }
    }
}
