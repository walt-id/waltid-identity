package id.walt.crypto.utils

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

object PromiseUtils {
    suspend fun <T> Promise<T>.await(): T = suspendCoroutine { cont ->
        then({ cont.resume(it) }, { cont.resumeWithException(it) })
    }

    suspend fun <T> await(promise: Promise<T>): T = suspendCoroutine { cont ->
        promise.then({ cont.resume(it) }, { cont.resumeWithException(it) })
    }
}
