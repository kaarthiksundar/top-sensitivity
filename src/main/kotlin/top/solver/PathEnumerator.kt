package top.solver

import top.data.Instance
import top.main.SetGraph
import top.data.Route

/**
 * Data class for constructing labels. These labels are used to enumerate all feasible paths in the graph.
 *
 * @param vertex Incident vertex of the path.
 * @param score Total score of the vertices in the partial path.
 * @param length length of the partial path.
 * @param parent Predecessor label.
 * @param visitedVertices The vertices which are visited in the partial path.
 */
data class Label(
    val vertex: Int,
    val score: Double,
    val length: Double,
    val parent: Label?,
    val visitedVertices: MutableList<Int>
) {
    override fun hashCode(): Int {
        return visitedVertices.hashCode()
    }
}

/**
 * Label extension function
 * @param label The label that needs to be extended
 * @param newVertex The incident vertex
 * @param edgeLength Length of the new edge
 * @param vertexScore Score of the incident vertex [newVertex]
 * @return returns the newly constructed label
 */
fun extend(
    label: Label,
    newVertex: Int,
    edgeLength: Double,
    vertexScore: Double,
): Label {
    val newVisitedVertices = label.visitedVertices.toMutableList()
    newVisitedVertices.add(newVertex)
    return Label(newVertex,
        label.score + vertexScore,
        label.length + edgeLength,
        label, newVisitedVertices)
}

/**
 * Function for enumerating all feasible paths i.e.,
 * that satisfy the path length constraint specified by [Instance.budget],
 * does not contain multiple visits to the same vertex,
 * starts at [Instance.source], and ends at [Instance.destination]
 *
 * @return Returns a list of [Route] objects corresponding to the feasible paths in [Instance.graph].
 *
 */
fun enumeratePaths(instance: Instance) : List<Route> {
    val graph: SetGraph = instance.graph

    val initialLabel = Label(instance.source, 0.0, 0.0, null, mutableListOf(instance.source))

    val unprocessedLabels = mutableListOf(initialLabel)

    val routes = mutableListOf<Route>()

    while (unprocessedLabels.isNotEmpty()) {
        val currentLabel = unprocessedLabels.last()
        if (currentLabel.vertex == instance.destination) {
            unprocessedLabels.removeLast()
            routes.add(generateRoute(currentLabel))
        }
        else {
            unprocessedLabels.removeLast()
            val outgoingEdges = graph.outgoingEdgesOf(currentLabel.vertex)
            for (e in outgoingEdges) {
                val edgeLength = graph.getEdgeWeight(e)
                val newLength = currentLabel.length + edgeLength
                val newVertex = graph.getEdgeTarget(e)
                if (newVertex in currentLabel.visitedVertices || newLength > instance.budget)
                    continue
                val newLabel = extend(currentLabel, newVertex, edgeLength, instance.scores[newVertex])
                unprocessedLabels.add(newLabel)
            }
        }
    }
    return routes
}

/**
 * Finds an initial route for a single vehicle to the destination. Used as the starting set of routes for the
 * column generation scheme
 */
fun initialRoutes(instance: Instance, numRoutes: Int) : List<Route>{
    val graph: SetGraph = instance.graph

    val initialLabel = Label(instance.source, 0.0, 0.0, null, mutableListOf(instance.source))

    val unprocessedLabels = mutableListOf(initialLabel)

    val routes = mutableListOf<Route>()

    loop@ while (unprocessedLabels.isNotEmpty()){
        val currentLabel = unprocessedLabels.last()
        if (currentLabel.vertex == instance.destination) {
            unprocessedLabels.removeLast()
            routes.add(generateRoute(currentLabel))

            if (routes.size >= numRoutes)
                break@loop
        }
        else {
            unprocessedLabels.removeLast()
            val outgoingEdges = graph.outgoingEdgesOf(currentLabel.vertex)
            for (e in outgoingEdges) {
                val edgeLength = graph.getEdgeWeight(e)
                val newLength = currentLabel.length + edgeLength
                val newVertex = graph.getEdgeTarget(e)
                if (newVertex in currentLabel.visitedVertices || newLength > instance.budget)
                    continue
                val newLabel = extend(currentLabel, newVertex, edgeLength, instance.scores[newVertex])
                unprocessedLabels.add(newLabel)
            }
        }
    }

    return routes
}

/**
 * Function to generate a route from destination label
 * @param destinationLabel Label with vertex as destination
 * @return Returns an object of the [Route] class
 */
fun generateRoute(destinationLabel: Label) : Route {
    val path = mutableListOf<Int>()
    var label: Label? = destinationLabel
    while (label!!.parent != null) {
        path.add(label.vertex)
        label = label.parent
    }
    path.add(label.vertex)
    path.reverse()
    return Route(path, destinationLabel.score, destinationLabel.length)
}
