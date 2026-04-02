package id.walt.webdatafetching.config

enum class HttpEngine {
    /** Official Coroutine-IO engine */
    CIO,

    /** Java HTTP Client (HTTP2 capable), requires Java 11 */
    Java,

    /** Supports HTTP/1.1 and HTTP/2 */
    Apache5,

    /** OkHttp (optimized for Android, also works on JVM) */
    OkHttp,

    /** Android on Android, Darwin on macOS/iOS, WinHttp on Windows, Curl on Linux, Js/fetch on JavaScript */
    Native

    // Supports ONLY HTTP2, requires Java 11
    // Jetty
}
