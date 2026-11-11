package com.raynigon.raylevation.base.service

import com.raynigon.raylevation.srtm.service.SRTMService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Service
import kotlin.system.exitProcess

/**
 * The CliRunner defines the application mode, after the application was initialised.
 * The application can either be running as a server and provide the endpoints,
 * or can be started in a setup mode, to fill the raylevation db with data.
 */
interface CliRunner : ApplicationRunner {
    /**
     * Process the given application arguments. Only non-option arguments
     * (i.e. arguments which are not of the form `--name=value`) are
     * considered as a potential command.
     *
     * @param args    All application arguments passed on process start
     */
    override fun run(args: ApplicationArguments)

    /**
     * Exit the process and provide an error message,
     * containing the command which was not known.
     *
     * @param cmd     The name of the unknown command
     */
    fun exitUnknownCommand(cmd: String)
}

/**
 * A single CLI command, bundling its name, help text and action
 * in one place, so the usage text and the dispatch logic can't drift apart.
 */
data class CliCommandDefinition(
    val name: String,
    val description: String,
    val action: () -> Unit,
)

/**
 * Implementation of the [CliRunner] interface
 */
@Service
class CliRunnerImpl(
    private val srtmService: SRTMService,
    private val exit: (Int) -> Unit = { code -> exitProcess(code) },
) : CliRunner {
    companion object {
        const val CLI_CMD_ENV_NAME = "RAYLEVATION_CLI_CMD"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val commands: List<CliCommandDefinition> =
        listOf(
            CliCommandDefinition(
                "update-srtm",
                "Download the configured SRTM tiles and update the raylevation database",
            ) { srtmService.updateRaylevationDB() },
        )

    override fun run(args: ApplicationArguments) {
        val cmd = args.nonOptionArgs.firstOrNull() ?: System.getenv(CLI_CMD_ENV_NAME)
        if (cmd.isNullOrEmpty()) return
        val command = commands.firstOrNull { it.name.equals(cmd, ignoreCase = true) }
        if (command == null) {
            exitUnknownCommand(cmd)
            return
        }
        try {
            command.action()
            logger.info("All CLI Commands were executed successfully")
            exit(0)
        } catch (e: Throwable) {
            logger.error("Unexpected Exception during Command Execution", e)
            // Exit Code according to https://tldp.org/LDP/abs/html/exitcodes.html
            exit(1)
        }
    }

    override fun exitUnknownCommand(cmd: String) {
        logger.error(usage(cmd))
        // Exit Code according to https://tldp.org/LDP/abs/html/exitcodes.html
        exit(127)
    }

    private fun usage(cmd: String): String {
        val commandLines = commands.joinToString("\n") { "  ${it.name.padEnd(14)}${it.description}" }
        return """
            |Unknown command '$cmd'.
            |Usage: raylevation-server [command] [--spring.option=value ...]
            |       or set $CLI_CMD_ENV_NAME=<command>
            |Commands:
            |$commandLines
            |Without a command the server is started.
            """.trimMargin()
    }
}
