package top.data

import top.main.SetGraph


/**
 * Represents a problem instance of the Euclidean team orienteering problem.
 *
 * @param graph Object representing the directed graph for the problem instance
 * @param numVertices Int: Number of vertices in the graph
 * @param numVehicles Int: Number of vehicles used in the team orienteering problem
 * @param source Int: Index of the source node in the graph
 * @param destination Int: Index of the destination node in the graph
 * @param scores List<Double>: List of scores at each node in the graph
 * @param budget Double: Maximum amount of time/length allotted for each vehicle.
 */

data class Instance(
    val graph: SetGraph,
    val numVertices: Int,
    val numVehicles: Int,
    val source: Int,
    val destination: Int,
    val scores: List<Double>,
    val budget: Double
)