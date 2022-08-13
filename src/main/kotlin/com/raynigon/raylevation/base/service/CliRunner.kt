package com.raynigon.raylevation.base.service

import com.raynigon.raylevation.srtm.service.SRTMService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Service
import kotlin.system.exitProcess

interface CliRunner : CommandLineRunner {

    override fun run(vararg args: String?)

    fun exitUnknownCommand(cmd: String)
}

@Service
class CliRunnerImpl(
    private val srtmService: SRTMService
) : CliRunner {

    companion object {
        const val CLI_CMD_ENV_NAME = "RAYLEVATION_CLI_CMD"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun run(vararg args: String?) {
        val cmd = args.firstOrNull() ?: System.getenv(CLI_CMD_ENV_NAME)
        if (cmd.isNullOrEmpty()) return
        try {
            when (cmd.lowercase()) {
                "update-srtm" -> srtmService.updateRaylevationDB()
                else -> exitUnknownCommand(cmd)
            }
            logger.info("All CLI Commands were executed successfully")
            exitProcess(0)
        } catch (e: Throwable) {
            logger.error("Unexpected Exception during Command Execution", e)
            // Exit Code according to https://tldp.org/LDP/abs/html/exitcodes.html
            exitProcess(1)
        }
    }

    override fun exitUnknownCommand(cmd: String) {
        logger.error("Unknown command was given: $cmd")
        // Exit Code according to https://tldp.org/LDP/abs/html/exitcodes.html
        exitProcess(127)
    }
}
