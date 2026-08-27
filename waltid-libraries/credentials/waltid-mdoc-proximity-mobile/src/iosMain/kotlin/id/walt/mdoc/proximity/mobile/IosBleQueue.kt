@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.mdoc.proximity.mobile

import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** CoreBluetooth managers in this module are created with the main dispatch queue. */
internal fun runOnIosBleQueue(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
}
