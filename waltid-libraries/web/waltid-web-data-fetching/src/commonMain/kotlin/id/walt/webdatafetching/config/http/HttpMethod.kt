package id.walt.webdatafetching.config.http

import kotlinx.serialization.Serializable
import io.ktor.http.HttpMethod as KtorHttpMethod

@Serializable
enum class HttpMethod(val ktorHttpMethod: KtorHttpMethod) {
    Get(KtorHttpMethod.Get),
    Post(KtorHttpMethod.Post),
    Put(KtorHttpMethod.Put),
    Patch(KtorHttpMethod.Patch),
    Delete(KtorHttpMethod.Delete),
    Head(KtorHttpMethod.Head),
    Options(KtorHttpMethod.Options),
}
