package id.walt.walletdemo.compose.android

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class WalletImageLoaderInitializationTest {
    @Test
    fun cancellingAnImageBodyReadKeepsTheLoaderUsable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = SingletonImageLoader.get(context)
        fun request(url: String) = ImageRequest.Builder(context).data(url)
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()

        ImageResponse(stalled = true).use { server ->
            val pending = async(Dispatchers.IO) { loader.execute(request(server.url)) }
            withTimeout(5_000) {
                // Cancel during the actual socket read, not at an arbitrary point after enqueueing.
                while (server.bodyStarted.count > 0 || Thread.getAllStackTraces().values.none { stack ->
                        stack.any { it.className.contains("RawSourceChannel") || it.className.startsWith("coil3.network.") } &&
                            stack.any { it.className.contains("Socket") && it.methodName.contains("read", ignoreCase = true) }
                    }) delay(10)
            }
            pending.cancel()
            server.releaseBody()
            withTimeout(5_000) { pending.join() }
            assertTrue("Cancelled image request must not produce a result", pending.isCancelled)
        }
        ImageResponse(stalled = false).use { server ->
            assertTrue(withTimeout(5_000) { loader.execute(request(server.url)) } is SuccessResult)
        }
    }

    @Test
    fun imageUseBeforeMainActivityAndRecreationKeepsTheConfiguredLoader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Provider activities and offer-art prefetch can use Coil before the main wallet UI.
        val loader = SingletonImageLoader.get(context)

        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { assertSame(loader, SingletonImageLoader.get(it)) }
            // Recreate immediately so resume still cancels a check the first launch left open.
            activity.recreate()
            activity.onActivity { assertSame(loader, SingletonImageLoader.get(it)) }
        }
    }

    private class ImageResponse(stalled: Boolean) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val socket = AtomicReference<Socket>()
        private val failure = AtomicReference<Throwable>()
        private val release = CountDownLatch(if (stalled) 1 else 0)
        val url = "http://127.0.0.1:${server.localPort}/image.png"
        val bodyStarted = CountDownLatch(1)
        private val worker = thread(name = "wallet-image-response", isDaemon = true) {
            runCatching {
                server.accept().use { connection ->
                    socket.set(connection)
                    connection.soTimeout = 5_000
                    val input = connection.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                    val image = ByteArrayOutputStream().use { output ->
                        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        bitmap.recycle()
                        output.toByteArray()
                    }
                    val output = connection.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${image.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(image, 0, 1)
                    output.flush()
                    bodyStarted.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "Image body was never released" }
                    runCatching { output.write(image, 1, image.size - 1) }
                }
            }.onFailure(failure::set)
        }

        fun releaseBody() = release.countDown()

        override fun close() {
            releaseBody()
            worker.join(1_000)
            socket.get()?.close()
            server.close()
            assertFalse("Image server did not stop", worker.isAlive)
            assertNull("Image server failed", failure.get())
        }
    }
}
