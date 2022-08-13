package com.raynigon.raylevation.infrastructure.process

import org.slf4j.Logger
import java.io.InputStream
import java.lang.Thread.sleep

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
