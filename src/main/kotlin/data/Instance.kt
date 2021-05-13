package data

import org.jgrapht.graph.DefaultWeightedEdge
import java.io.File
import kotlin.math.sqrt

data class Coords(val x: Double, val y: Double)

/*

    Class responsible for storing the relevant information for a problem instance.

    The text files are organized as follows.

    1   n   n_val           <- Number of nodes
    2   m   m_val           <- Number of vehicles
    3   b   b_val           <- Budget of an individual agent (i.e., max time or length)
    4   x   y   p           <- x-coordinate, y-coordinate, and prize of a node
    .
    .
    .
    4+n x   y   p
    5+n                     <- Intentional blank line

    NOTE: The index of the source and destination are is not specified. The source is assumed to always be node 0
          and the destination is always taken to be node n-1 (i.e., the last node)

          Indexing in Kotlin starts at 0.

 */

/*
 TODO: Determine what parameters need to be stored for each instance
*/

class Instance(
    private val name: String,
    private val path: String
)
{
    // Reading the text file and storing as a List of Strings. Each entry corresponds to a line in sequential order
    private var lines = File(path + name).readLines()

    private val source = 0
    private val graph = SetGraph(DefaultWeightedEdge::class.java)

    /*
        Regex functions for parsing specific lines
     */

    private fun parseCoords(line: String): Coords{
        /*
            Passing the line

            x   y   p

            where x and y are the coordinates of the given node and p is the prize associated with it. The values
            are separated using a single tab, so the delimiter of the regular expression is set to \t. The first
            two values that are read are then returned as a coordinate pair.
         */
        val values: List<Double> = line.split("[ \t]".toRegex()).map{
            it.toDouble()
        }

        return Coords(values[0], values[1])
    }

    private fun parsePrize(line: String): Double{
        /*
            Passing the line

            x   y   p

            where x and y are the coordinates of the given node and p is the prize associated with it. The values
            are separated using a single tab, so the delimiter of the regular expression is set to \t. The last value
            cooresponding to the prize is then returned.
         */
        val values: List<Double> = line.split("[ \t]".toRegex()).map{
            it.toDouble()
        }

        return values[2]
    }

    /*
        Parsing the lines of the text file to get relevant information
     */

    private val prizes = lines.subList(3, lines.size).map(::parsePrize)
    private val numVertices = lines[0].split("[ \t]".toRegex()).last().toInt()
    private val numVehicles = lines[1].split("[ \t]".toRegex()).last().toInt()
    private val budget = lines[2].split("[ \t]".toRegex()).last().toDouble()
    private val vertexCoords = lines.subList(3, lines.size).map(::parseCoords)
    private val destination = vertexCoords.size - 1

    init{
        createGraph(vertexCoords)
    }

    private fun eucDist(c1: Coords, c2: Coords): Double{

        /*

            Returns the Euclidean distance between two pair of coordinates in the xy-plane.

         */


        val x1 = c1.x
        val y1 = c1.y

        val x2 = c2.x
        val y2 = c2.y

        return sqrt((x2 - x1)*(x2 - x1) + (y2 - y1)*(y2 - y1))
    }

    private fun createGraph(vertexCoords: List<Coords>){

        /*
            Methods available for the graph.

            (i)     graph.addVertex(i)  <- Adds vertex with index i associated with it

            (ii)    graph.addEdge(i, j, edge)   <- Adds edge (i,j) with type of edge specified

                    Need to set

                            val edge = DefaultWeightedEdge()

            (iii)   graph.setEdgeWeight(edge, edgeLength)   <- Specify the weight associated with the edge

         */


        // Adding the vertices
        for (i in 0 until numVertices)
            graph.addVertex(i)

        // Adding the edges
        for (i in 0 until numVertices) {
            /*
                Not allowing directed edges from the destination to other nodes

                TODO: Check with Kaarthik that this is appropriate for this problem
             */
            if (i == destination)
                continue

            for (j in 0 until numVertices){

                /*
                    Not allowing self-loops

                 */

                 if (i == j)
                     continue

                /*
                    Not allowing directed edges into the source node

                    TODO: Check with Kaarthik that this is appropriate for this problem

                 */

                if (j == source)
                    continue

                // Euclidean distance between the two vertices
                val edgeLength: Double = eucDist(
                    vertexCoords[i],
                    vertexCoords[j]
                )

                /*

                    Avoid adding edges with a Euclidean distance already exceeding the budget allotted for each
                    vehicle, as these edges could never be taken.

                 */

                if (edgeLength > budget)
                    continue

                // Only valid directed edges (i, j) from this point on

                /*

                    Adding edges and setting the edge weights equal to the Euclidean distance between
                    the two nodes in the xy-plane as specified by the coordinates in the .txt file.

                 */

                val edge = DefaultWeightedEdge()
                graph.addEdge(i, j, edge)
                graph.setEdgeWeight(edge, edgeLength)

            }
        }

    }



    fun printInformation(){
        println("File Name: $name")
        println("Folder Path: $path")
        println("Source: $source")
        println("Destination: $destination")
        println("Number of Vertices: $numVertices")
        println("Number of Vehicles: $numVehicles")
        println("Budget: $budget")
        println("Prizes: $prizes")
        println("Coordinates: $vertexCoords")
        println("Graph: $graph")
    }


}
