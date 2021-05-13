package data

/*
 TODO: Determine what parameters need to be stored for each instance
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