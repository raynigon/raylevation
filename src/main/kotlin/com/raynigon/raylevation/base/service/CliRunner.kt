package com.raynigon.raylevation.base.service

import com.raynigon.raylevation.srtm.service.SRTMService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Service
import kotlin.system.exitProcess

/**
 * The CliRunner defines the application mode, after the application was initialised.
 * The application can either be running as a server and provide the endpoints,
 * or can be started in a setup mode, to fill the raylevation db with data.
 */
interface CliRunner : CommandLineRunner {
    /**
     * Process the given command line arguments
     *
     * @param args    All command line arguments passed on process start
     */
    override fun run(vararg args: String?)

    /**
     * Exit the process and provide an error message,
     * containing the command which was not known.
     *
     * @param cmd     The name of the unknown command
     */
    fun exitUnknownCommand(cmd: String)
}

/**
 * Implementation of the [CliRunner] interface
 */
@Service
class CliRunnerImpl(
    private val srtmService: SRTMService,
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
