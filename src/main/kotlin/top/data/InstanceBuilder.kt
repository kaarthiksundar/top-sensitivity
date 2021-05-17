package top.data

import mu.KotlinLogging
import org.jgrapht.graph.DefaultWeightedEdge
import top.main.SetGraph
import java.io.File
import kotlin.math.sqrt

private val log = KotlinLogging.logger {}

/**
 * Class to hold coordinates
 *
 * @param x Double: x coordinate
 * @param y Double: y coordinate
 */
data class Coords(val x: Double, val y: Double)

/**
 * Class the builds the instance object using information for a local .txt file.
 *
 * @param name String: Name of file including the .txt extension
 * @param path String: Local path of the folder containing the .txt file
 * @constructor Creates a new problem instance with the data extracted from the .txt file
 */

class InstanceBuilder(
    private val name: String,
    private val path: String
) {
    /**
     * @property instance object orienteering instance object
     * @property lines lines that will be read from given set instance file
     * @property source constant for source vertex of network
     * @property destination constant for destination vertices of network
     * @property numVertices number of vertices in network
     * @property numVehicles number of vehicles
     * @property budget total budget
     * @property graph directed weighted graph containing all the information
     */
    private var instance: Instance
    private val lines: List<String> = File(path + name).readLines()
    private val source = 0
    private val scores = lines.subList(3, lines.size).map(::parseScore)
    private val numVertices = lines[0].split("[ \t]".toRegex()).last().toInt()
    private val numVehicles = lines[1].split("[ \t]".toRegex()).last().toInt()
    private val budget = lines[2].split("[ \t]".toRegex()).last().toDouble()
    private val vertexCoords = lines.subList(3, lines.size).map(::parseCoords)
    private val destination = vertexCoords.size - 1
    private val graph = SetGraph(DefaultWeightedEdge::class.java)

    companion object {
        /**
         * Builds Coords objects from a String containing coordinates and score.
         *
         * @param line string that contains 3 doubles
         * @return Target object with given coordinates and score
         */
        private fun parseCoords(line: String): Coords {
            val values: List<Double> = line.split("[ \t]".toRegex()).map{
                it.toDouble()
            }
            return Coords(values[0], values[1])
        }
        /**
         * Parses score from a String containing coordinates and score.
         *
         * @param line string that contains 3 doubles
         * @return score of vertex
         */
        private fun parseScore(line: String): Double {
            val values: List<Double> = line.split("[ \t]".toRegex()).map{
                it.toDouble()
            }
            return values[2]
        }
    }

    init {
        createGraph()

        instance = Instance(
            graph = graph,
            numVertices = numVertices,
            numVehicles = numVehicles,
            source = source,
            destination = destination,
            scores = scores,
            budget = budget
        )
        log.info("instance created")
    }

    private fun createGraph() {
        fun getEdgeLength(c1: Coords, c2: Coords): Double {
            val x1: Double = c1.x
            val y1: Double = c1.y
            val x2: Double = c2.x
            val y2: Double = c2.y
            return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
        }
        /* add vertices to the graph */
        for (i in 0 until numVertices)
            graph.addVertex(i)
        /* add the edges to the graph */
        for (i in 0 until numVertices) {
            if (i == destination) continue
            for (j in 0 until numVertices) {
                if (i == j) continue
                val edgeLength: Double = getEdgeLength(vertexCoords[i], vertexCoords[j])
                if (edgeLength > budget) continue
                val edge = DefaultWeightedEdge()
                graph.addEdge(i, j, edge)
                graph.setEdgeWeight(edge, edgeLength)
            }
        }
    }

    fun getInstance(): Instance = instance
}