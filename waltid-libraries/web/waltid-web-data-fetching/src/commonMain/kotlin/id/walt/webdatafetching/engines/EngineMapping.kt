package id.walt.webdatafetching.engines

import id.walt.webdatafetching.config.HttpEngine

object EngineMapping {

    fun getEngineInstance(engine: HttpEngine): WebDataFetcherHttpEngine = when (engine) {
        HttpEngine.CIO -> CIOEngine
        HttpEngine.Java -> JavaEngine
        HttpEngine.Apache5 -> Apache5Engine
        HttpEngine.OkHttp -> OkHttpEngine
        HttpEngine.Native -> NativeEngine
        //HttpEngine.Jetty -> JettyEngine // Temporarily disabled
    }

}
