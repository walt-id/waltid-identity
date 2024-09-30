package id.walt.permissions.utils

import kotlinx.coroutines.flow.*

object FlowUtils {

    /**
     * Splits the original Flow into a Pair of flows,
     * where *first* Flow contains elements for which [predicate] yielded `true`,
     * while *second* Flow contains elements for which [predicate] yielded `false`.
     */
    /*inline fun <T> Flow<T>.partition(crossinline predicate: suspend (T) -> Boolean): Pair<Flow<T>, Flow<T>> {
        val flowA = filter { predicate(it) }
        val flowB = filter { !predicate(it) }

        return flowA to flowB
    }*/
/*
    inline fun <T> Flow<T>.partition(crossinline predicate: suspend (T) -> Boolean): Pair<Flow<T>, Flow<T>> {
        // Create channels for positive and negative flows
        val positiveChannel = Channel<T>(Channel.UNLIMITED)
        val negativeChannel = Channel<T>(Channel.UNLIMITED)

        // CoroutineScope to manage coroutine lifecycle
        val scope = CoroutineScope(Dispatchers.Default)

        val thisFlow = this

        // Launch a coroutine to process the original flow
        scope.launch {
            thisFlow.collect { value ->
                if (predicate(value)) {
                    positiveChannel.send(value)
                } else {
                    negativeChannel.send(value)
                }
            }
            // Close channels when done
            positiveChannel.close()
            negativeChannel.close()
        }

        // Convert channels to flows
        val positiveFlow = positiveChannel.consumeAsFlow()
        val negativeFlow = negativeChannel.consumeAsFlow()

        return positiveFlow to negativeFlow
    }*/


    /**
     * Extension function to lazily split a string and emit the parts as a Flow<String>
     */
   fun String.splitIntoFlow(delimiter: String): Flow<String> = flow {
        var startIndex = 0
        var currentIndex: Int

        while (startIndex < length) {
            currentIndex = indexOf(delimiter, startIndex)

            if (currentIndex == -1) {
                // Emit the last part (or the full string if no delimiter is found)
                emit(substring(startIndex))
                break
            } else {
                // Emit the substring up to the delimiter
                emit(substring(startIndex, currentIndex))
                // Move past the delimiter
                startIndex = currentIndex + delimiter.length
            }
        }
    }

}
