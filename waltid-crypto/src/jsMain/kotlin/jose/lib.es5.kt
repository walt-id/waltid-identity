@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

package tsstdlib

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Date

@Suppress("DEPRECATION")
external interface DateConstructor {
    @nativeInvoke
    operator fun invoke(): String
    var prototype: Date
    fun parse(s: String): Number
    fun UTC(
        year: Number,
        month: Number,
        date: Number = definedExternally,
        hours: Number = definedExternally,
        minutes: Number = definedExternally,
        seconds: Number = definedExternally,
        ms: Number = definedExternally
    ): Number

    fun now(): Number
}

external interface ConcatArray<T> {
    var length: Number

    @nativeGetter
    operator fun get(n: Number): T?

    @nativeSetter
    operator fun set(n: Number, value: T)
    fun join(separator: String = definedExternally): String
    fun slice(start: Number = definedExternally, end: Number = definedExternally): Array<T>
}

external interface ArrayConstructor {
    fun <T> from(iterable: Iterable<T>): Array<T>
    fun <T> from(iterable: ArrayLike<T>): Array<T>
    fun <T, U> from(iterable: Iterable<T>, mapfn: (v: T, k: Number) -> U, thisArg: Any = definedExternally): Array<U>
    fun <T, U> from(iterable: Iterable<T>, mapfn: (v: T, k: Number) -> U): Array<U>
    fun <T, U> from(iterable: ArrayLike<T>, mapfn: (v: T, k: Number) -> U, thisArg: Any = definedExternally): Array<U>
    fun <T, U> from(iterable: ArrayLike<T>, mapfn: (v: T, k: Number) -> U): Array<U>
    fun <T> of(vararg items: T): Array<T>

    @nativeInvoke
    operator fun invoke(arrayLength: Number = definedExternally): Array<Any>

    @nativeInvoke
    operator fun invoke(): Array<Any>

    @nativeInvoke
    operator fun <T> invoke(arrayLength: Number): Array<T>

    @nativeInvoke
    operator fun <T> invoke(vararg items: T): Array<T>
    fun isArray(arg: Any): Boolean
    var prototype: Array<Any>
}

external interface PromiseLike<T> {
    fun then(
        onfulfilled: ((value: T) -> Any?)? = definedExternally,
        onrejected: ((reason: Any) -> Any?)? = definedExternally
    ): PromiseLike<dynamic /* TResult1 | TResult2 */>
}

external interface ArrayLike<T> {
    var length: Number

    @nativeGetter
    operator fun get(n: Number): T?

    @nativeSetter
    operator fun set(n: Number, value: T)
}

typealias Pick<T, K> = Any

typealias Record<K, T> = Any

typealias Exclude<T, U> = Any

typealias Omit<T, K> = Any

typealias Parameters<T> = Any

external interface ArrayBufferTypes {
    var ArrayBuffer: ArrayBuffer
}

external interface ArrayBufferConstructor {
    var prototype: ArrayBuffer
    fun isView(arg: Any): Boolean
}

external interface Uint8ArrayConstructor {
    fun from(
        arrayLike: Iterable<Number>,
        mapfn: (v: Number, k: Number) -> Number = definedExternally,
        thisArg: Any = definedExternally
    ): Uint8Array

    fun from(arrayLike: Iterable<Number>): Uint8Array
    fun from(arrayLike: Iterable<Number>, mapfn: (v: Number, k: Number) -> Number = definedExternally): Uint8Array
    var prototype: Uint8Array
    var BYTES_PER_ELEMENT: Number
    fun of(vararg items: Number): Uint8Array
    fun from(arrayLike: ArrayLike<Number>): Uint8Array
    fun <T> from(arrayLike: ArrayLike<T>, mapfn: (v: T, k: Number) -> Number, thisArg: Any = definedExternally): Uint8Array
    fun <T> from(arrayLike: ArrayLike<T>, mapfn: (v: T, k: Number) -> Number): Uint8Array
}
