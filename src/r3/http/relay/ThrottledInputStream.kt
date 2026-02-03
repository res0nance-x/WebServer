package r3.http.relay

import java.io.InputStream
import kotlin.math.max

/**
 * Wraps an InputStream and applies throttling to aim for a target throughput (bytes per second).
 * After each read, it calculates the actual throughput and sleeps if needed to maintain the target rate.
 * If targetBytesPerSecond is 0 or less, no throttling is applied.
 */
class ThrottledInputStream(
    private val wrapped: InputStream,
    private val targetBytesPerSecond: Long
) : InputStream() {
    private var totalBytesRead: Long = 0
    private val startTimeMillis: Long = System.currentTimeMillis()

    override fun read(): Int {
        val result = wrapped.read()
        if (result >= 0) maybeThrottle(1)
        return result
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val result = wrapped.read(b, off, len)
        if (result > 0) maybeThrottle(result)
        return result
    }

    override fun read(b: ByteArray): Int {
        val result = wrapped.read(b)
        if (result > 0) maybeThrottle(result)
        return result
    }

    override fun skip(n: Long): Long {
        return wrapped.skip(n)
    }

    // This might not be supported in Android API levels below 26
    override fun skipNBytes(n: Long) {
        wrapped.skipNBytes(n)
    }

    override fun close() {
        wrapped.close()
    }

    override fun available(): Int {
        return wrapped.available()
    }

    private fun maybeThrottle(bytesRead: Int) {
        if (targetBytesPerSecond <= 0) return
        totalBytesRead += bytesRead
        val now = System.currentTimeMillis()
        val elapsedMillis = max(1, now - startTimeMillis)
        val expectedMillis = (totalBytesRead * 1000) / targetBytesPerSecond
        if (expectedMillis > elapsedMillis) {
            val sleepMillis = expectedMillis - elapsedMillis
            Thread.sleep(sleepMillis)
        }
    }
}
