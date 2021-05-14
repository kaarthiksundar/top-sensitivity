package top.main

import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph

/**
 * Custom exception to throw problem-specific exception.
 */
class TOPException(message: String) : Exception(message)

typealias SetGraph = SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>

/**
 *  Function that returns the number of vertices in the graph
 */
fun SetGraph.numVertices() = this.vertexSet().size

/**
 * Returns weight of edge between [from] vertex and [to] vertex.
 */
fun SetGraph.getEdgeWeight(from: Int, to: Int): Double {
    return this.getEdgeWeight(this.getEdge(from, to))
}

/**
 * Creates a shallow copy of the graph.
 *
 * The returned SetGraph object is new, but its edges are references.
 */
fun SetGraph.getCopy(): SetGraph {
    val graphCopy = SetGraph(DefaultWeightedEdge::class.java)
    Graphs.addGraph(graphCopy, this)
    return graphCopy
}