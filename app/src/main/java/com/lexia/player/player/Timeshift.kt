package com.lexia.player.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.lexia.player.data.Http
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Enregistre un flux direct (MPEG-TS) dans un fichier pendant la pause,
 * pour pouvoir le relire ensuite en différé (« timeshift »).
 */
class LiveRecorder(private val url: String, val file: File, private val maxBytes: Long) {
    val bytesWritten = AtomicLong(0)

    @Volatile var running = false
        private set

    @Volatile private var stopRequested = false
    private var call: okhttp3.Call? = null

    fun start() {
        file.delete()
        running = true
        thread(name = "timeshift", isDaemon = true) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
                val c = Http.client.newCall(request)
                call = c
                c.execute().use { response ->
                    val input = response.body?.byteStream() ?: return@use
                    FileOutputStream(file).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (!stopRequested && bytesWritten.get() < maxBytes) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            bytesWritten.addAndGet(n.toLong())
                        }
                    }
                }
            } catch (_: Exception) {
                // Coupure du flux : la lecture en différé s'arrêtera à la fin de ce qui a été enregistré.
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        stopRequested = true
        runCatching { call?.cancel() }
    }

    fun delete() {
        stop()
        file.delete()
    }
}

/** Lit le fichier en cours d'enregistrement en attendant les nouvelles données (comme « tail -f »). */
@OptIn(UnstableApi::class)
class GrowingFileDataSource(private val recorder: LiveRecorder) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var position = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        file = RandomAccessFile(recorder.file, "r").also { it.seek(dataSpec.position) }
        position = dataSpec.position
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val f = file ?: return C.RESULT_END_OF_INPUT
        var waitedMs = 0
        while (true) {
            val available = recorder.bytesWritten.get() - position
            if (available > 0) {
                val n = f.read(buffer, offset, minOf(length.toLong(), available).toInt())
                if (n > 0) {
                    position += n
                    bytesTransferred(n)
                    return n
                }
            }
            if (!recorder.running || waitedMs > 30_000) return C.RESULT_END_OF_INPUT
            if (Thread.interrupted()) throw InterruptedIOException()
            try {
                Thread.sleep(40)
            } catch (e: InterruptedException) {
                throw InterruptedIOException()
            }
            waitedMs += 40
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { file?.close() }
        file = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
