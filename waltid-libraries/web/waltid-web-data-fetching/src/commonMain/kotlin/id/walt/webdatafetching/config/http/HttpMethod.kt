package id.walt.webdatafetching.config.http

import io.ktor.http.HttpMethod as KtorHttpMethod

enum class HttpMethod(val ktorHttpMethod: KtorHttpMethod) {
    Get(KtorHttpMethod.Get),
    Post(KtorHttpMethod.Post),
    Put(KtorHttpMethod.Put),
    Patch(KtorHttpMethod.Patch),
    Delete(KtorHttpMethod.Delete),
    Head(KtorHttpMethod.Head),
    Options(KtorHttpMethod.Options),
}
