package top.main

import ilog.cplex.IloCplex
import top.data.Instance
import top.data.Parameters

/**
 * Class used to manage the solution process.
 */

class Controller {

    private lateinit var instance: Instance
    private lateinit var cplex: IloCplex
    private lateinit var resultsPath: String
    private val results = sortedMapOf<String, Any>()    // Don't understand why this can be val instead of var

    /**
     * Parses [args], the given command line arguments
     */
    fun parseArgs(args: Array<String>) {
        val parser = CliParser()
        parser.main(args)
        resultsPath = parser.outputPath

        Parameters.initialize(
            instanceName = parser.instanceName,
            instancePath = parser.instancePath,
            timeLimitInSeconds = parser.timeLimitInSeconds
        )

        results["instance_name"] = parser.instanceName
        results["instance_path"] = parser.instancePath
        results["time_limit_in_seconds"] = parser.timeLimitInSeconds

    }



}