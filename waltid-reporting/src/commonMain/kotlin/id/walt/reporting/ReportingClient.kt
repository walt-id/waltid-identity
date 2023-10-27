package id.walt.reporting
import it.justwrote.kjob.InMem
import it.justwrote.kjob.kjob
import it.justwrote.kjob.kron.Kron
import it.justwrote.kjob.kron.KronModule
import java.time.Instant

object ReportingClient {

    fun startReporting() {
        val kjob = kjob(InMem) {
            extension(KronModule)
        }.start()

        /*kjob(Kron).kron(PrintStuff) {
            maxRetries = 3
            execute {
                println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
            }
        }

        kjob(Kron).kron(PrintMoreStuff) {
            execute {
                println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
            }
        }*/
    }

}
