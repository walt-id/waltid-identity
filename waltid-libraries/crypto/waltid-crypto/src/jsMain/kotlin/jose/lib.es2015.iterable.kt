@file:Suppress("PropertyName", "unused", "PackageDirectoryMismatch")

package tsstdlib

import kotlin.js.Promise

external interface IteratorYieldResult<TYield> {
    var done: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var value: TYield
}

external interface IteratorReturnResult<TReturn> {
    var done: Boolean
    var value: TReturn
}

external interface Iterator<T, TReturn, TNext> {
    fun next(vararg args: Any /* JsTuple<> | JsTuple<TNext> */): dynamic /* IteratorYieldResult<T> | IteratorReturnResult<TReturn> */
    val `return`: ((value: TReturn) -> dynamic)?
    val `throw`: ((e: Any) -> dynamic)?
}

typealias Iterator__1<T> = Iterator<T, Any, Nothing?>

external interface Iterable<T>

external interface IterableIterator<T> : Iterator__1<T>

external interface PromiseConstructor {
    var prototype: Promise<Any>
    fun all(values: Any /* JsTuple<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?> | JsTuple<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?> | JsTuple<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?> | JsTuple<Any?, Any?, Any?, Any?, Any?, Any?, Any?> | JsTuple<Any?, Any?, Any?, Any?, Any?, Any?> | JsTuple<Any?, Any?, Any?, Any?, Any?> | JsTuple<Any?, Any?, Any?, Any?> | JsTuple<Any?, Any?, Any?> | JsTuple<Any?, Any?> */): Promise<dynamic /* JsTuple<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> | JsTuple<T1, T2, T3, T4, T5, T6, T7, T8, T9> | JsTuple<T1, T2, T3, T4, T5, T6, T7, T8> | JsTuple<T1, T2, T3, T4, T5, T6, T7> | JsTuple<T1, T2, T3, T4, T5, T6> | JsTuple<T1, T2, T3, T4, T5> | JsTuple<T1, T2, T3, T4> | JsTuple<T1, T2, T3> | JsTuple<T1, T2> */>
    fun <T> all(values: Array<Any? /* T | PromiseLike<T> */>): Promise<Array<T>>
    fun <T> race(values: Array<T>): Promise<Any>
    fun <T> reject(reason: Any = definedExternally): Promise<T>
    fun <T> resolve(value: T): Promise<T>
    fun <T> resolve(value: PromiseLike<T>): Promise<T>
    fun resolve(): Promise<Unit>
    fun <T> all(values: Iterable<Any? /* T | PromiseLike<T> */>): Promise<Array<T>>
    fun <T> race(values: Iterable<T>): Promise<Any>
    fun <T> race(values: Iterable<Any? /* T | PromiseLike<T> */>): Promise<T>
}
