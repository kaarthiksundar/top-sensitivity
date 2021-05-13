package data

/*
 TODO: Determine what parameters need to be stored for each instance
*/

/**
 * Represents a problem instance of the Euclidean team orienteering problem.
 *
 * @param name String: Name of file including the .txt extension
 * @param path String: Local path of the folder containing [name]
 * @param graph Object representing the directed graph for the problem instance
 * @param numVertices Int: Number of vertices in the graph
 * @param numVehicles Int: Number of vehicles used in the team orienteering problem
 * @param source Int: Index of the source node in the graph
 * @param destination Int: Index of the destination node in the graph
 * @param prizes List<Double>: List of prizes at each node in the graph
 * @param budget Double: Maximum amount of time/length allotted for each vehicle.
 */

data class Instance(
    val name: String,
    val path: String,
    val graph: SetGraph,
    val numVertices: Int,
    val numVehicles: Int,
    val source: Int,
    val destination: Int,
    val prizes: List<Double>,
    val budget: Double
)