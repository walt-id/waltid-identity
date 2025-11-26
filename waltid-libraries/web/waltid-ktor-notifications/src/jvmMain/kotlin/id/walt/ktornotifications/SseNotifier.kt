package id.walt.ktornotifications

import id.walt.ktornotifications.core.KtorSessionUpdate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

object SseNotifier {
    private val flows =
        ConcurrentHashMap<String, MutableSharedFlow<KtorSessionUpdate>>()

    /**
     * Returns a SharedFlow for the given target.
     * If a flow for the target doesn't exist, it will be created.
     */
    fun getSseFlow(target: String): SharedFlow<KtorSessionUpdate> {
        val newFlow = MutableSharedFlow<KtorSessionUpdate>(replay = 10)
        return flows.computeIfAbsent(target) { newFlow }.asSharedFlow()
    }

    /**
     * Emits an event to the flow associated with the given target.
     */
    suspend fun notify(target: String, event: KtorSessionUpdate) {
        flows[target]?.emit(event)
    }
}
