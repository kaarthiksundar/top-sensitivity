package top.main

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Class for parsing the command line arguments
 */
class CliParser : CliktCommand() {
    /**
     * Name of the file containing the instance including the .txt extension.
     */
    val instanceName: String by option(
        "-n",
        help = "Instance file name with .txt extension"
    ).default("p2.2.k.txt")

    /**
     * File path for the folder containing the instance file.
     */
    val instancePath: String by option(
        "-p",
        help = "File path for the folder containing the instance file"
    ).default("./data/Set_21_234/")

    /**
     * Path to the file with output KPIs
     */
    val outputPath: String by option(
        "-o",
        help = "Path to file with output KPIs"
    ).default("./logs/results.yaml")

    /**
     * Maximum time for the solver in seconds.
     */
    val timeLimitInSeconds: Int by option(
        "-t",
        help = "Maximum time for solver in seconds"
    ).int().default(3600).validate{
        require(it > 0){
            "Time limit should be a strictly positive integer"
        }
    }

    /**
     * New fleet size used in sensitivity analysis
     */
    val adjustedFleetSize : Int by option(
        "-f",
        help= "New fleet size used in sensitivity analysis"
    ).int().default(3).validate {
        require(it > 0) {
            "Fleet size should be a strictly positive integer"
        }
    }

    /**
     * List of vertices to be removed in sensitivity analysis
     */
    val verticesToRemove : List<Int> by option(
        "-v",
        help="List of vertices to be removed in sensitivity analysis"
    ).int().multiple()

    override fun run() {
        log.debug("reading command line arguments...")
    }
}