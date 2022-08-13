package com.raynigon.raylevation.infrastructure.process

import org.slf4j.Logger
import java.io.InputStream
import java.lang.Thread.sleep

/**
 * Convert the stdout and stderr streams of a process into messages and log them with a [Logger].
 * All messages from stdout are logged as INFO and all messages from stderr are logged as ERROR.
 */
class ProcessLogger(
    private val process: Process,
    private val logger: Logger,
    private val prefix: String
) {
    private val stdoutThread: Thread = Thread { parse(process.inputStream, logger::info) }
    private val stderrThread: Thread = Thread { parse(process.errorStream, logger::error) }

    init {
        stdoutThread.start()
        stderrThread.start()
    }

    /**
     * Waits for all Thread to be finished.
     * This ensures the underlying process was stopped and no more data is available from the streams
     */
    fun join() {
        stdoutThread.join()
        stderrThread.join()
    }

    private fun parse(stream: InputStream, logger: (String) -> Unit) {
        var buffer = ""
        while (process.isAlive || stream.available() > 0) {
            if (stream.available() <= 0) {
                sleep(10)
                continue
            }
            buffer += String(stream.readNBytes(stream.available()))
            while (buffer.contains("\n")) {
                val end = buffer.indexOf("\n")
                val line = buffer.substring(0, end)
                logger(prefix + line)
                buffer = buffer.substring(end + 1)
            }
        }
    }
}
