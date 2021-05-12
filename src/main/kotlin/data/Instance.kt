package data

import data.SetGraph


/*

    Class responsible for storing the relevant information for a problem instance.

 */

class Instance(
    val graph: SetGraph,
    val budget: Double,
    val sourceTarget: Int,
    val destinationTarget: Int,
    val numVehicles: Int,
    val numTargets: Int,
    private val vertexScores: List<Double>,
    private val targetOfVertex: List<Int>,
    private val verticesInTarget: List<List<Int>>
)
{

    val numVertices = graph.numVertices()
    val targetScores = (0 until numTargets).map{
        val vertices = verticesInTarget[it]
        if (vertices.isEmpty()) 0.0 else vertexScores[vertices[0]]
    }

    fun whichTarget(i: Int): Int = targetOfVertex[i]

    fun getVertices(i: Int): List<Int> = verticesInTarget[i]

    fun getScore(i: Int): Double = vertexScores[i]
}