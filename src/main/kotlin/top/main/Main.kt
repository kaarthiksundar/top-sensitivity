package top.main

import ilog.cplex.IloCplex
import mu.KotlinLogging
import top.data.Instance
import top.data.InstanceBuilder
import top.data.Parameters
import top.solver.SetCoverModel
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

    //log.info("Running solver")
    //runSolver(instance, parameters)

    log.info("Enumerating Routes")
    val routes = enumeratePaths(instance)

    log.info("Number of feasible paths: ${routes.size}")

    log.info("Making CPLEX object")
    val cplex = IloCplex()
    log.info("CPLEX object created")

    log.info("Creating set cover object")
    val setCoverModel = SetCoverModel(cplex)
    log.info("Creating set cover model")
    setCoverModel.createModel(instance, routes)
    log.info("Set cover model created")
    log.info("Solving set cover model")
    setCoverModel.solve()
    log.info("Set cover model solved")

    log.info("Getting solution values")
    val routeVariableValues: List<Double> = setCoverModel.getSolution()

    log.info("Route Values: $routeVariableValues")
    log.info("Objective Value: ${setCoverModel.objective}")
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