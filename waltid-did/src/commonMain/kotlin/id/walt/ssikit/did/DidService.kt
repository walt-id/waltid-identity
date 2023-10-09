package id.walt.ssikit.did

import id.walt.core.crypto.keys.Key
import id.walt.ssikit.did.DidUtils.methodFromDid
import id.walt.ssikit.did.registrar.DidRegistrar
import id.walt.ssikit.did.registrar.DidRegistrarRegistrations
import id.walt.ssikit.did.registrar.DidResult
import id.walt.ssikit.did.registrar.dids.DidCreateOptions
import id.walt.ssikit.did.resolver.DidResolver
import id.walt.ssikit.did.resolver.DidResolverRegistrations
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject

object DidService {
    private val log = KotlinLogging.logger {}

    /* - Resolver & registrar configuration - */

    val didResolvers = ArrayList<DidResolver>()
    val didRegistrars = ArrayList<DidRegistrar>()

    val resolverMethods = HashMap<String, DidResolver>() // method -> resolver
    val registrarMethods = HashMap<String, DidRegistrar>() // method -> registrar

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

    suspend fun init() {
        registerAllResolvers(DidResolverRegistrations.didResolvers)
        registerAllRegistrars(DidRegistrarRegistrations.didRegistrars)

        updateResolversForMethods()
        updateRegistrarsForMethods()

        log.debug { "INIT -> RESOLVERS:  $resolverMethods" }
        log.debug { "INIT -> REGISTRARS: $registrarMethods" }
    }

    fun registerRegistrar(registrar: DidRegistrar) = if (registrar !in didRegistrars) didRegistrars.add(registrar) else false
    fun unregisterRegistrar(registrar: DidRegistrar) = didRegistrars.remove(registrar)

    fun registerResolverForMethod(method: String, resolver: DidResolver) = resolverMethods.put(method, resolver)
    fun registerRegistrarForMethod(method: String, registrar: DidRegistrar) = registrarMethods.put(method, registrar)
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

    suspend fun updateRegistrarsForMethods() {
        didRegistrars.forEach { registrar ->
            val methods = registrar.getSupportedMethods()
            when {
                methods.isSuccess -> methods.getOrThrow().forEach { method ->
                    registerRegistrarForMethod(method, registrar)
                }

                else -> log.warn { "DID Registrar ${registrar.name} cannot be used, error: ${methods.exceptionOrNull().let { it?.message ?: it.toString() }}" }
            }
        }
    }


    private fun getResolverForDid(did: String): DidResolver {
        val method = methodFromDid(did)
        return resolverMethods[method] ?: throw IllegalArgumentException("No resolver for did method: $method")
    }

    /* - Did methods - */
    suspend fun resolve(did: String): Result<JsonObject> =
        getResolverForDid(did).resolve(did)

    suspend fun resolveToKey(did: String): Result<Key> =
        getResolverForDid(did).resolveToKey(did)

    private fun getRegistrarForMethod(method: String): DidRegistrar =
        registrarMethods[method] ?: throw IllegalArgumentException("No registrar for did method: $method")

    suspend fun register(options: DidCreateOptions) {
        getRegistrarForMethod(options.method).create(options)
    }

    suspend fun registerByKey(method: String, key: Key, options: DidCreateOptions = DidCreateOptions(method, emptyMap())): DidResult =
        getRegistrarForMethod(method).createByKey(key, options)

    fun update() {
        TODO("Not yet implemented")
    }

    fun deactivate() {
        TODO("Not yet implemented")
    }

}
