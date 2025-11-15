package id.walt.did.dids

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidUtils.methodFromDid
import id.walt.did.dids.registrar.DidRegistrar
import id.walt.did.dids.registrar.DidRegistrarRegistrations
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.registrar.dids.*
import id.walt.did.dids.resolver.DidResolver
import id.walt.did.dids.resolver.DidResolverRegistrations
import id.walt.did.dids.resolver.LocalResolver
import id.walt.did.utils.EnumUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
object DidService {
    private val log = KotlinLogging.logger {}

    /* - Resolver & registrar configuration - */

    val didResolvers = ArrayList<DidResolver>()
    val didRegistrars = ArrayList<DidRegistrar>()

    /** method -> resolver */
    val resolverMethods = HashMap<String, DidResolver>()

    /** method -> registrar */
    val registrarMethods = HashMap<String, DidRegistrar>()

    fun registerResolver(resolver: DidResolver) = if (resolver !in didResolvers) didResolvers.add(resolver) else false
    fun unregisterResolver(resolver: DidResolver) = didResolvers.remove(resolver)

    fun registerAllResolvers(resolvers: Collection<DidResolver>) {
        resolvers.reversed() // priority
            .forEach { resolver ->
                registerResolver(resolver)
            }
    }

    fun registerAllRegistrars(registrars: Collection<DidRegistrar>) {
        registrars.reversed() // priority
            .forEach { registrar ->
                registerRegistrar(registrar)
            }
    }

    /**
     * Use a walt.id curated configuration of resolvers and registrars, including remote resolvers and registrars.
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun init(resolverUrl: String? = null, registrarUrl: String? = null) {
        registerAllResolvers(DidResolverRegistrations.curatedDidResolvers(resolverUrl))
        registerAllRegistrars(DidRegistrarRegistrations.curatedDidRegistrars(registrarUrl))

        updateResolversForMethods()
        updateRegistrarsForMethods()

        log.debug { "INIT -> RESOLVERS:  $resolverMethods" }
        log.debug { "INIT -> REGISTRARS: $registrarMethods" }
    }

    /**
     * Do not initiate any remote resolvers or registrars, start out with a minimal set of resolvers and registrars get started with.
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun minimalInit() {
        registerAllResolvers(setOf(LocalResolver()))
        registerAllRegistrars(setOf(LocalRegistrar()))
        updateResolversForMethods()
        updateRegistrarsForMethods()
    }

    fun registerRegistrar(registrar: DidRegistrar) =
        if (registrar !in didRegistrars) didRegistrars.add(registrar) else false

    fun unregisterRegistrar(registrar: DidRegistrar) = didRegistrars.remove(registrar)

    fun registerResolverForMethod(method: String, resolver: DidResolver) = resolverMethods.put(method, resolver)
    fun registerRegistrarForMethod(method: String, registrar: DidRegistrar) = registrarMethods.put(method, registrar)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun updateResolversForMethods() {
        didResolvers.forEach { resolver ->
            val methods = resolver.getSupportedMethods()
            when {
                methods.isSuccess -> methods.getOrThrow().forEach { method ->
                    registerResolverForMethod(method, resolver)
                }

                else -> log.warn { "DID Resolver ${resolver.name} cannot be used, error: ${methods.exceptionOrNull()?.message}" }
            }
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun updateRegistrarsForMethods() {
        didRegistrars.forEach { registrar ->
            val methods = registrar.getSupportedMethods()
            when {
                methods.isSuccess -> methods.getOrThrow().forEach { method ->
                    registerRegistrarForMethod(method, registrar)
                }

                else -> log.warn {
                    "DID Registrar ${registrar.name} cannot be used, error: ${
                        methods.exceptionOrNull().let { it?.message ?: it.toString() }
                    }"
                }
            }
        }
    }


    private fun getResolverForDid(did: String): DidResolver {
        val method = methodFromDid(did)
        return resolverMethods[method] ?: throw IllegalArgumentException("No resolver for did method: $method")
    }

    /* - Did methods - */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun resolve(did: String): Result<JsonObject> =
        getResolverForDid(did).resolve(did)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun resolveToKey(did: String): Result<Key> =
        getResolverForDid(did).resolveToKey(did)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun resolveToKeys(did: String): Result<Set<Key>> =
        getResolverForDid(did).resolveToKeys(did)

    private fun getRegistrarForMethod(method: String): DidRegistrar =
        registrarMethods[method] ?: throw IllegalArgumentException("No registrar for did method: $method")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun register(options: DidCreateOptions) =
        getRegistrarForMethod(options.method).create(options)

    //@JvmBlocking
    //@JvmAsync
    //@JsPromise
    @JsExport.Ignore
    suspend fun registerByKey(
        method: String, key: Key, options: DidCreateOptions = DidCreateOptions(method, emptyMap()),
    ): DidResult = getRegistrarForMethod(method).createByKey(key, options)

    @JvmBlocking
    @JvmAsync
    @JsExport.Ignore
    suspend fun javaRegisterByKey(
        method: String, key: Key, options: DidCreateOptions,
    ): DidResult = registerByKey(method, key, options)

    private fun getDidOptions(method: String, args: Map<String, JsonPrimitive>) =
        when (method.lowercase()) {
            "key" ->
                DidKeyCreateOptions(
                    args["key"]?.let { EnumUtils.enumValueIgnoreCase<KeyType>(it.content) } ?: KeyType.Ed25519,
                    args["useJwkJcsPub"]?.let { it.content.toBoolean() } ?: false)

            "jwk" -> DidJwkCreateOptions()
            "web" -> DidWebCreateOptions(domain = args["domain"]?.content ?: "", path = args["path"]?.content ?: "")
            "cheqd" -> DidCheqdCreateOptions(network = args["network"]?.content ?: "testnet")
            else -> throw IllegalArgumentException("DID method not supported for auto-configuration: $method")
        }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun registerDefaultDidMethodByKey(method: String, key: Key, args: Map<String, JsonPrimitive>): DidResult {
        val options = getDidOptions(method, args)
        val result = registerByKey(method, key, options)

        return result
    }

    fun update() {
        TODO("Not yet implemented")
    }

    fun deactivate() {
        TODO("Not yet implemented")
    }

}
