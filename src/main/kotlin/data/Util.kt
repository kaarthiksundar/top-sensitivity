package data

import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph

typealias SetGraph = SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>

/*
    Function that returns the number of vertices in the graph
 */
fun SetGraph.numVertices() = this.vertexSet().size

/*
    Function that returns the edge weight of a given directed edge (from, to)
 */
fun SetGraph.getEdgeWeight(from: Int, to: Int): Double
{
    return this.getEdgeWeight(this.getEdge(from, to))
}

