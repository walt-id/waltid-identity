package id.walt

import id.walt.web.plugins.configureMonitoring
import id.walt.web.plugins.configureOpenApi
import id.walt.web.plugins.configureRouting
import id.walt.web.plugins.configureSerialization
import io.klogging.Level
import io.klogging.config.loggingConfiguration
import io.klogging.rendering.RENDER_ANSI
import io.klogging.sending.STDERR
import io.klogging.sending.STDOUT
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

var isLoggingSetup = false

fun loggingSetup() {
    if (isLoggingSetup) return

    isLoggingSetup = true
    loggingConfiguration(true) {
        sink("stdout", RENDER_ANSI, STDOUT)
        sink("stderr", RENDER_ANSI, STDERR)

        logging {
            fromLoggerBase("io.ktor", stopOnMatch = true)
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
}

fun startExample(wait: Boolean = true, jwt: Boolean): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    loggingSetup()
    return embeddedServer(CIO, port = 8088, host = "0.0.0.0", module = {
        this.module(jwt)
    })
        .start(wait = wait)
}

fun Application.module(jwt: Boolean) {
    // configureSecurity()
    // configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureRouting()
    configureOpenApi()

    testApp(jwt)

    /*collectRoutes().forEach {
        println(it)
    }*/
}
