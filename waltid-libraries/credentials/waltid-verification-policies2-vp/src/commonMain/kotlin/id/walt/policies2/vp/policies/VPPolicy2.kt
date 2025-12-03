package id.walt.policies2.vp.policies

import id.walt.crypto.utils.JsonUtils.toJsonElement
import korlibs.io.lang.portableSimpleName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration
import kotlin.time.measureTimedValue

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("policy")
@Serializable
sealed class VPPolicy2() {

    abstract val id: String
    abstract val description: String

    init {
        //check(id.isNotEmpty()) { "Initialized ${this::class.portableSimpleName} VP policy with empty ID!" }
        //check(description.isNotEmpty()) { "Initialized ${this::class.portableSimpleName} VP policy with empty description!" }
    }

    @Serializable
    data class PolicyRunError(
        val error: String,
        val message: String?,
        val cause: PolicyRunError?
    ) {
        constructor(ex: Throwable) : this(
            error = ex::class.simpleName ?: ex::class.portableSimpleName,
            message = ex.message,
            cause = ex.cause?.let { PolicyRunError(ex.cause!!) }
        )
    }

    @Serializable
    data class PolicyRunResult(
        @SerialName("policy_executed")
        val policyExecuted: VPPolicy2,

        val success: Boolean,
        val results: Map<String, JsonElement>,
        val errors: List<PolicyRunError>,

        @SerialName("execution_time")
        val executionTime: Duration
    )

    internal suspend fun runPolicy(
        block: suspend VPPolicyRunContext.() -> Result<Unit>,
    ): PolicyRunResult {
        val policyContext = VPPolicyRunContext()

        val timedRunResult = measureTimedValue {
            runCatching {
                block.invoke(policyContext)
            }
        }
        val runResult = timedRunResult.value

        if (runResult.isFailure) {
            policyContext.addError(runResult.exceptionOrNull()!!)
        }

        return PolicyRunResult(
            policyExecuted = this,
            success = runResult.isSuccess && policyContext.errors.isEmpty(),
            results = policyContext.results.mapValues { v -> runCatching { v.value.toJsonElement() }.recoverCatching { ex -> JsonPrimitive(v.value.toString()) }.getOrElse { JsonPrimitive("?") } },
            errors = policyContext.errors.map { PolicyRunError(it) },
            executionTime = timedRunResult.duration
        )
    }

    class VPPolicyRunContext() {

        val resultMutex = Mutex()

        /** Only use for reading results after run */
        val results = LinkedHashMap<String, Any?>()

        val errorMutex = Mutex()

        /** Only use for reading results after run */
        val errors = ArrayList<Throwable>()

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
