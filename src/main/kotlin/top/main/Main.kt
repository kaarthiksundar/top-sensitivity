package top.main

import branchandbound.api.BranchAndBoundApi
import branchandbound.api.SelectionStrategy
import ilog.cplex.IloCplex
import mu.KotlinLogging
import top.data.Instance
import top.data.InstanceBuilder
import top.data.Parameters
import top.solver.*
import kotlin.system.measureTimeMillis
import mu.KLogging

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

    /*
    val cplex = IloCplex()
    val cgs = ColumnGenerationSolver(instance, cplex, parameters)

    val t1 = measureTimeMillis {
        cgs.solve()
    }

    log.info("LP Objective: ${cgs.lpObjective}")
    log.info("LP Solution: ${cgs.lpSolution}")
    log.info("LP Integer: ${cgs.lpIntegral}")
    log.info("Time Elapsed (sec): ${t1 / 1000.0}")
    log.info("MIP Objective: ${cgs.mipObjective}")
    log.info("MIP Solution: ${cgs.mipSolution}")

     */

    val numSolvers = 8

    val idGenerator = generateSequence(0L) {it + 1}.iterator()

    val solvers = List(numSolvers) {
        TOPSolver(instance, IloCplex(), parameters)
    }

    val rootNode = TOPNode(parent = null, id = idGenerator.next())
    val t1 = measureTimeMillis {
        val solution = BranchAndBoundApi.runBranchAndBound(
            solvers, SelectionStrategy.BEST_BOUND, rootNode
        ) {
            TOPBranch((it as TOPNode), idGenerator, instance)
        }
        log.info("Number of Nodes: ${solution!!.numCreatedNodes}")
        log.info("Number of Feasible Nodes: ${solution!!.numFeasibleNodes}")
        log.info("Solution Objective: ${solution!!.incumbent!!.mipObjective}")
    }

    log.info("Time Elapsed (sec): ${t1 / 1000.0}")
}

private fun parseArgs(args: Array<String>): Parameters {
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