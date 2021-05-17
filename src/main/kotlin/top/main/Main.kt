package top.main

import mu.KotlinLogging
import top.data.InstanceBuilder
import top.data.Parameters
import top.solver.enumerateAllPathsWithinBudget

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

   val feasiblePathList = enumerateAllPathsWithinBudget(instance)

   println("Instance: ${parameters.instanceName}")
   println("Graph: ${instance.graph}")
   println("Source: ${instance.source}")
   println("Destination: ${instance.destination}")
   println("Budget: ${instance.budget}")
   println("Number of Feasible Paths: ${feasiblePathList.size}")
   println("Feasible Path List: $feasiblePathList")

}

fun parseArgs(args: Array<String>) : Parameters {
    val parser = CliParser()
    parser.main(args)
    return Parameters(
        instanceName = parser.instanceName,
        instancePath = parser.instancePath,
        timeLimitInSeconds = parser.timeLimitInSeconds,
        outputPath = parser.outputPath
    )
}