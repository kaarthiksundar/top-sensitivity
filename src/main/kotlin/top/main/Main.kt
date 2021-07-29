package top.main

import branchandbound.api.BranchAndBoundApi
import branchandbound.api.SelectionStrategy
import branchandbound.api.Solution
import ilog.cplex.IloCplex
import mu.KotlinLogging
import top.data.InstanceBuilder
import top.data.Parameters
import top.solver.*
import kotlin.system.measureTimeMillis
import kotlin.math.min

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

    val numSolvers = 8
    val idGenerator = generateSequence(0L) {it + 1}.iterator()

    val solvers = List(numSolvers) {
        TOPSolver(instance, IloCplex(), parameters)
    }

    val rootNode = TOPNode(parent = null, id = idGenerator.next())
    var solution: Solution?
    val t1 = measureTimeMillis {
        solution = BranchAndBoundApi.runBranchAndBound(
            solvers, SelectionStrategy.BEST_BOUND, rootNode
        ) {
            TOPBranch((it as TOPNode), idGenerator, instance)
        }

        log.info("Number of Nodes: ${solution!!.numCreatedNodes}")
        log.info("Number of Feasible Nodes: ${solution!!.numFeasibleNodes}")
        log.info("Solution Objective: ${solution!!.incumbent!!.mipObjective}")
    }

    log.info("Time Elapsed (sec): ${t1 / 1000.0}")

    log.info { "Upper Bound From Sensitivity Analysis: ${getUpperBound(solution, rootNode, parameters)}" }

}

private fun parseArgs(args: Array<String>): Parameters {
    val parser = CliParser()
    parser.main(args)
    return Parameters(
        instanceName = parser.instanceName,
        instancePath = parser.instancePath,
        timeLimitInSeconds = parser.timeLimitInSeconds,
        outputPath = parser.outputPath
    )
}

private fun getUpperBound(solution: Solution?, rootNode : TOPNode, parameters: Parameters) : Double {

    var currentNode = solution!!.incumbent
    val mipUpperBound: Double

    while (true) {
        (currentNode as TOPNode)

        // Finding adjusted MIP upper bound (B^t in the paper)

        if (currentNode.children == null) {
            // Current node in the tree is a terminal node

            if (currentNode.lpFeasible) {
                // Current node has a feasible solution, so set equal to dual LP upper bound
                currentNode.adjustedMIPUpperBound = currentNode.dualLPUpperBound
            }
            else{
                // Current node does not have a feasible solution, so use Phase I dual values
                if (currentNode.dualLPUpperBound < - parameters.eps)
                    currentNode.adjustedMIPUpperBound = Double.NEGATIVE_INFINITY
                else
                    currentNode.adjustedMIPUpperBound = Double.POSITIVE_INFINITY
            }
        }
        else {
            // Current node is a non-terminal node (i.e., it has children nodes)

            // Update mip upper bound based on the upper bounds of the current node's children
            val childrenUpperBounds : List<Double> = (0 until currentNode.children!!.size).map {
                (currentNode as TOPNode).children!![it].adjustedMIPUpperBound
            }

            currentNode.adjustedMIPUpperBound = min(currentNode.dualLPUpperBound, childrenUpperBounds.maxOrNull()!!)
        }

        if (currentNode.id == rootNode.id) {
            mipUpperBound = currentNode.adjustedMIPUpperBound
            break
        }

        // Moving up the tree
        currentNode = currentNode.parent
    }

    return mipUpperBound

}