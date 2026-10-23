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

fun startExample(
    wait: Boolean = true,
    jwt: Boolean,
    host: String = "0.0.0.0",
    port: Int = 8088,
    setupLogging: Boolean = true,
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    if (setupLogging) loggingSetup()
    val server = embeddedServer(CIO, port = port, host = host, module = {
        this.module(jwt)
    })
    return try {
        server.start(wait = wait)
    } catch (failure: Throwable) {
        runCatching { server.stop(0, 1_000) }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
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
