package id.walt.credentials.issuance

import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.w3c.vc.vcs.W3CVC
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Merges a credential's data with a mapping: values of the mapping are written into the data, and template values
 * such as `<timestamp>` or `<uuid>` are replaced by the result of the named data function.
 *
 * One engine serves every credential format. Formats differ only in a [MergePolicy] - W3C and SD-JWT payloads
 * append arrays and keep templates inside arrays literal, mdoc namespaces replace arrays and evaluate templates
 * everywhere - and in how `jwt:` keys are treated.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object CredentialDataMergeUtils {

    private val log = KotlinLogging.logger { }

    fun JsonPrimitive.isTemplate(): Boolean {
        val content = this.content
        return content.length > 2 && content.first() == '<' && content.last() == '>' && !content.contains(' ')
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getTemplateData(
        functionCall: String,
        dataFunctions: Map<String, suspend (FunctionCall) -> JsonElement>,
        context: Map<String, JsonElement>,
        functionHistory: MutableMap<String, JsonElement>
    ): JsonElement {
        val cmdLine = functionCall.substring(1, functionCall.length - 1)
        val cmd = cmdLine.substringBefore(":")
        val func = dataFunctions[cmd]
            ?: throw IllegalArgumentException("Unknown dynamic data function \"$cmd\" at call: $functionCall")

        val hasArgs = cmd.length < cmdLine.length

        val result = if (hasArgs) {
            val args = cmdLine.substring(cmd.length + 1)
            func.invoke(FunctionCall(cmd, functionHistory, context, args))
        } else {
            try {
                func.invoke(FunctionCall(cmd, null, context, null))
            } catch (e: NullPointerException) {
                log.error { e }
                throw IllegalArgumentException("Could not execute dynamic data function \"$cmd\" - missing argument! At function call: $cmdLine")
            }
        }
        if (result is JsonPrimitive) {
            functionHistory[cmd] = result
        }

        log.debug { "Evaluated mapping function $cmd" }
        return result
    }

    /**
     * How a mapping is merged into data. The two presets are the behaviours the formats have always had, kept
     * exactly so that no issued credential changes.
     */
    @JsExport.Ignore
    data class MergePolicy(
        /** A mapped array is appended to an existing one (true), or replaces it (false). */
        val appendArrays: Boolean,
        /** Templates inside a mapped array are evaluated (true), or copied literally (false). */
        val evaluateTemplatesInArrays: Boolean,
        /** A mapped object over a value that is not an object replaces it (true), or is an error (false). */
        val objectReplacesNonObject: Boolean,
        /** An empty mapped object still sets the key (true), or leaves the data unchanged (false). */
        val emptyObjectSetsKey: Boolean,
    ) {
        companion object {
            /** W3C and SD-JWT payloads. */
            val APPEND = MergePolicy(appendArrays = true, evaluateTemplatesInArrays = false, objectReplacesNonObject = false, emptyObjectSetsKey = false)

            /** mdoc namespace payloads. */
            val REPLACE = MergePolicy(appendArrays = false, evaluateTemplatesInArrays = true, objectReplacesNonObject = true, emptyObjectSetsKey = true)
        }
    }

    /** Read-only template inputs and the DID derived during this merge. */
    private class MergeContext(private val supplied: Map<String, JsonElement>) : AbstractMap<String, JsonElement>() {
        override val entries get() = supplied.entries
        override fun get(key: String) = supplied[key]
        var derivedSubjectDid: JsonPrimitive? = null
    }

    /** One merge: the policy, the template inputs, and the call history `<last:...>` reads, shared across it. */
    private class Merge(
        private val policy: MergePolicy,
        context: Map<String, JsonElement>,
        private val functions: Map<String, suspend (FunctionCall) -> JsonElement>,
    ) {
        private val context = MergeContext(context)
        private val history = HashMap<String, JsonElement>()

        suspend fun evaluate(functionCall: String): JsonElement = getTemplateData(functionCall, functions, context, history)

        private suspend fun template(value: JsonPrimitive): JsonElement =
            if (value.isString && value.isTemplate()) evaluate(value.content) else value

        /** Writes the [mapping] of [key] into [data]. */
        suspend fun into(data: MutableMap<String, JsonElement>, key: String, mapping: JsonElement) {
            when (mapping) {
                is JsonPrimitive -> data[key] = template(mapping)

                is JsonObject -> {
                    if (mapping.isEmpty() && !policy.emptyObjectSetsKey) return
                    val existing = data[key]
                    val target = when {
                        existing == null -> mutableMapOf()
                        existing is JsonObject -> existing.toMutableMap()
                        policy.objectReplacesNonObject -> mutableMapOf()
                        else -> {
                            val cause = runCatching { existing.jsonObject }.exceptionOrNull()
                            throw IllegalArgumentException(
                                "Invalid mapping for credential, when processing \"$key\": ${cause?.message}",
                                cause
                            )
                        }
                    }
                    mapping.forEach { (childKey, childMapping) -> into(target, childKey, childMapping) }
                    data[key] = JsonObject(target)
                }

                is JsonArray -> {
                    val items = if (policy.evaluateTemplatesInArrays) evaluated(mapping) else mapping
                    val existing = data[key]
                    data[key] = if (policy.appendArrays && existing is JsonArray) JsonArray(existing + items) else items
                }
            }
        }

        private suspend fun evaluated(mapping: JsonArray): JsonArray = JsonArray(mapping.map { item ->
            when (item) {
                is JsonPrimitive -> template(item)
                is JsonObject -> JsonObject(mutableMapOf<String, JsonElement>().also { target ->
                    item.forEach { (k, v) -> into(target, k, v) }
                })
                is JsonArray -> evaluated(item)
            }
        })
    }


    data class MergeResult(val vc: W3CVC, val results: Map<String, JsonElement>)
    data class JsonMergeResult(val vc: JsonObject, val results: Map<String, JsonElement>)


    data class FunctionCall(
        val func: String,
        val history: Map<String, JsonElement>?,
        val context: Map<String, JsonElement>,
        val args: String?
    ) {
        fun fromContext(): JsonElement {
            return context[func] ?: throw IllegalArgumentException("Cannot find in context: $func")
        }

        internal suspend fun subjectDid(holderKey: IssuanceHolderKey): JsonElement {
            val merge = context as? MergeContext
            return context["subjectDid"] ?: merge?.derivedSubjectDid
                ?: holderKey.deriveSubjectDid().also { merge?.derivedSubjectDid = it }
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun W3CVC.mergeWithMapping(
        mapping: JsonObject,
        context: Map<String, JsonElement>,
        data: Map<String, suspend (FunctionCall) -> JsonElement>
    ): MergeResult {
        val merge = Merge(MergePolicy.APPEND, context, data)
        val vcm = this.toMutableMap()
        val results = HashMap<String, JsonElement>()
        mapping.forEach { (k, v) ->
            if (!k.startsWith("jwt:")) {
                merge.into(vcm, k, v)
            } else {
                results[k] = merge.evaluate(v.jsonPrimitive.content)
            }
        }
        return MergeResult(W3CVC(vcm), results)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun JsonObject.mergeSDJwtVCPayloadWithMapping(
        mapping: JsonObject,
        context: Map<String, JsonElement>,
        data: Map<String, suspend (FunctionCall) -> JsonElement>
    ): JsonObject {
        val merge = Merge(MergePolicy.APPEND, context, data)
        val vcm = this.toMutableMap()
        mapping.forEach { (k, v) ->
            if (!k.startsWith("jwt:")) {
                merge.into(vcm, k, v)
            } else {
                vcm[k.removePrefix("jwt:")] = merge.evaluate(v.jsonPrimitive.content)
            }
        }
        return vcm.toJsonObject()
    }

    /** Validate namespace mapping configuration before the credential data is populated. */
    fun validateMdocNamespaceMappingShape(mapping: JsonObject) {
        mapping.forEach { (key, value) ->
            require(key != "validFrom" && key != "validUntil" && key != "expectedUpdate") {
                "mapping.$key is not an mdoc namespace object; set msoData.$key for MSO validity"
            }
            require(value is JsonObject) {
                "mapping.$key must be a JSON object of element mappings"
            }
        }
    }

    /** Resolve the mapping against the final [credentialData], including claims supplied during authorization. */
    fun JsonObject.mdocNamespaceMapping(credentialData: JsonObject): JsonObject? {
        if (isEmpty()) return null
        validateMdocNamespaceMappingShape(this)
        keys.forEach { key ->
            val credentialValue = requireNotNull(credentialData[key]) {
                "mapping.$key does not match a credentialData namespace object"
            }
            require(credentialValue is JsonObject) {
                "mapping.$key requires credentialData.$key to be a JSON object of namespace claims"
            }
        }
        return this
    }

    /**
     * Merge for mDoc namespace payloads, with [MergePolicy.REPLACE]: mapped arrays replace existing ones and
     * templates inside them are evaluated. W3C and SD-JWT merges use [MergePolicy.APPEND].
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun JsonObject.mergeMdocPayloadWithMapping(
        mapping: JsonObject,
        context: Map<String, JsonElement>,
        data: Map<String, suspend (FunctionCall) -> JsonElement>,
    ): JsonObject {
        val merge = Merge(MergePolicy.REPLACE, context, data)
        val result = this.toMutableMap()
        mapping.forEach { (k, v) -> merge.into(result, k, v) }
        return JsonObject(result)
    }
}
