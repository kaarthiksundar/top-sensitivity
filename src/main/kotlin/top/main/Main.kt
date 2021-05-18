package top.main

import mu.KotlinLogging
import top.data.Instance
import top.data.InstanceBuilder
import top.data.Parameters
import top.solver.enumeratePaths

private val log = KotlinLogging.logger {}

/**
 * Main function.
 */
fun main(args: Array<String>) {
    val parameters: Parameters = parseArgs(args)
    log.info("CLI arguments parsed")
    val instance = InstanceBuilder(
        name = parameters.instanceName,
        path = parameters.instancePath
    ).getInstance()

    log.info("Running solver")
    runSolver(instance, parameters)
}

fun parseArgs(args: Array<String>) : Parameters {
    val parser = CliParser()
    parser.main(args)
    return Parameters(
        instanceName = parser.instanceName,
        instancePath = parser.instancePath,
        timeLimitInSeconds = parser.timeLimitInSeconds,
        outputPath = parser.outputPath,
        algorithm = parser.algorithm
    )
}

fun runSolver(instance: Instance, parameters: Parameters) {
    if (parameters.algorithm == 0) {
        val routes = enumeratePaths(instance)
        log.info("number of feasible paths: ${routes.size}")
    }
}