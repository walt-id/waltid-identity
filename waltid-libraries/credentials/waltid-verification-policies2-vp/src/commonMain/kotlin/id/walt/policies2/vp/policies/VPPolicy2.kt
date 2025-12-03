package id.walt.policies2.vp.policies

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("policy")
@Serializable
sealed class VPPolicy2() {

    abstract val id: String
    abstract val description: String

    init {
        check(id.isNotEmpty()) { "Initialized ${this::class.qualifiedName} VP policy with empty ID!" }
        check(description.isNotEmpty()) { "Initialized ${this::class.qualifiedName} VP policy with empty description!" }
    }

    internal suspend fun runPolicy(
        block: suspend VPPolicyRunContext.() -> Result<Unit>,
    ) {
        val policyContext = VPPolicyRunContext()

        val runResult = runCatching {
            block.invoke(policyContext)
        }
        if (runResult.isFailure) {
            policyContext.addError(runResult.exceptionOrNull()!!)
        }

    }

    class VPPolicyRunContext() {

        val resultMutex = Mutex()
        private val results = LinkedHashMap<String, Any?>()

        val errorMutex = Mutex()
        private val errors = ArrayList<Throwable>()

        private var success = false

        /*suspend fun addResult(key: String, value: Any) = addJsonResult(key, JsonPrimitive(value))
        suspend fun addResult(key: String, value: Boolean) = addJsonResult(key, JsonPrimitive(value))
        suspend fun addResult(key: String, value: List<JsonElement>) = addJsonResult(key, JsonArray(value))
        */
        suspend fun addResult(key: String, value: Any?) {
            resultMutex.withLock {
                results[key] = value
            }
        }

        suspend fun addHashResult(key: String, subkey: String, value: Any) {
            resultMutex.withLock {
                if (!results.containsKey(key)) {
                    results[key] = HashMap<String, Any>()
                }
                @Suppress("UNCHECKED_CAST")
                (results[key] as HashMap<String, Any>)[subkey] = value
            }
        }

        suspend fun addHashListResult(key: String, subkey: String, value: Any) {
            resultMutex.withLock {
                if (!results.containsKey(key)) {
                    results[key] = HashMap<String, List<Any>>()
                }
                @Suppress("UNCHECKED_CAST")
                val hm = results[key] as HashMap<String, List<Any>>
                if (!hm.containsKey(subkey)) {
                    hm[subkey] = ArrayList()
                }

                @Suppress("UNCHECKED_CAST")
                (hm[subkey] as ArrayList<Any>).add(value)
            }
        }


        fun CoroutineScope.addOptionalJsonResult(key: String, valueBlock: suspend () -> JsonElement) = launch {
            addResult(key, valueBlock.invoke())
        }

        fun CoroutineScope.addOptionalResult(key: String, valueBlock: suspend () -> String) = launch {
            addResult(key, JsonPrimitive(valueBlock.invoke()))
        }

        suspend fun addError(error: Throwable) = errorMutex.withLock {
            errors.add(error)
        }

        suspend fun success(response: Unit = Unit): Result<Unit> {
            success = true
            return Result.success(response)
        }
    }

}
