package id.walt.authkit

import id.walt.authkit.plugins.configureMonitoring
import id.walt.authkit.plugins.configureRouting
import id.walt.authkit.plugins.configureSerialization
import io.klogging.Level
import io.klogging.config.loggingConfiguration
import io.klogging.rendering.RENDER_ANSI
import io.klogging.sending.STDERR
import io.klogging.sending.STDOUT
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

fun main() {

    loggingConfiguration(true) {
        sink("stdout", RENDER_ANSI, STDOUT)
        sink("stderr", RENDER_ANSI, STDERR)

        logging {
            fromLoggerBase("io.ktor.routing.Routing", stopOnMatch = true)
            fromMinLevel(Level.DEBUG) {
                toSink("stdout")
            }
        }
        logging {
            fromLoggerBase("org.sqlite.core.NativeDB", stopOnMatch = true)
            fromMinLevel(Level.DEBUG) {
                toSink("stdout")
            }
        }
        logging {
            fromMinLevel(Level.ERROR) {
                toSink("stderr")
            }
            inLevelRange(Level.TRACE, Level.WARN) {
                toSink("stdout")
            }
        }
        minDirectLogLevel(Level.TRACE)
    }

    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // configureSecurity()
    // configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureRouting()

    testApp()
}
