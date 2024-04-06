package id.walt.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import id.walt.did.dids.DidService
import kotlinx.coroutines.runBlocking

class Did : CliktCommand(help = "Run commands on DIDs") {

    //private val config by findOrSetObject { mutableMapOf<String, String>() }

    internal enum class ServiceType {
        LOCAL,
        LOCAL_REMOTE
    }

    private val serviceType by option(
        help = "Initialize a minimal set of local-only services, or a full set of all services (provides more resolving & registering functionality, but is slower)",
        completionCandidates = CompletionCandidates.Fixed(ServiceType.entries.map { it.name }.toSet())
    ).enum<ServiceType> { it.name.lowercase() }.default(ServiceType.LOCAL)

    override fun run() {
        //config[ServiceType::class] = serviceType.name
        initializeDidServices(serviceType)
    }

    companion object {
        internal fun initializeDidServices(serviceType: ServiceType) {
            println("Initializing $serviceType services...")

            runBlocking {
                when (serviceType) {
                    ServiceType.LOCAL -> DidService.minimalInit()
                    ServiceType.LOCAL_REMOTE -> DidService.init()
                }
            }
        }
    }

    internal class Create : CliktCommand("Create a DID") {
        //private val config by requireObject<Map<String, String>>()

        override fun run() {
            //val serviceType = ServiceType.valueOf(config[ServiceType::class])

            //initializeDidServices(serviceType)
            println("Creating DID: ")
        }
    }

}
