package top.solver

import top.data.Instance
import top.main.SetGraph

/**
 * Data class for constructing labels. These labels are used to enumerate all feasible paths in the graph.
 *
 * @param currentNode Current node the vehicle is located after taking some path
 * @param collectedPrize Sum of the prizes collected by the vehicle along some path to [currentNode]
 * @param pathLength Length of the path taken by the vehicle to [currentNode]
 * @param visitedNodes List of nodes that have been visited by the vehicle along the path taken
 * @param labelIndex Integer identifier of the label
 * @param predecessorIndex Integer identifier of the preceding label used to generate the current label through a
 * feasible extension.
 */
data class Label(val currentNode: Int,
                 val collectedPrize: Double,
                 val pathLength: Double,
                 val visitedNodes: List<Int>,
                 val labelIndex: Int,
                 val predecessorIndex: Int)

/**
 * Data class for storing a feasible path and its corresponding total prize and path length.
 *
 * @param path List of nodes that have been visited in chronological order
 * @param totalPrize Sum of the prizes collected by the vehicle after taking [path]
 * @param pathLength Distance traveled/time elapsed along [path]
 */
data class FeasiblePath(val path: List<Int>, val totalPrize: Double, val pathLength: Double)


/**
 * Function for enumerating all feasible paths for a single vehicle. The paths are feasible in the sense that the path
 * does not visit any node more than once and the total path length is less than a budget. The budget is contained
 * within [Instance] object [instance].
 *
 * Returns a list of [FeasiblePath] objects corresponding to the feasible paths in the graph.
 *
 */
fun enumerateAllPathsWithinBudget(instance: Instance) : List<FeasiblePath>
{
    val graph: SetGraph = instance.graph

    /*
        Creating the Label corresponding to the vehicle beginning at the source node.

        The predecessor index for the initial label is set to -1 to indicate there is no predecessor.
     */
    val initialLabel = Label(   currentNode = instance.source,
                                collectedPrize = 0.0,
                                pathLength = 0.0,
                                visitedNodes = listOf(instance.source),
                                labelIndex = 0,
                                predecessorIndex = -1)
    var maxLabelIndex = 0
    /*
        Mutable map for tracking which label is associated with a specific integer index value.

        The initial Label corresponding to the vehicle starting at the depot will be given the index value 0.
     */
    val labelsMap = mutableMapOf(0 to initialLabel)

    /*
        Mutable list of labels that need to be treated (i.e., checked for feasible extensions and then remove
        from the mutable list)
     */
    val untreatedLabels = mutableListOf(initialLabel)

    /*
        Mutable list of all feasible paths.
     */
    val feasiblePathList = mutableListOf<FeasiblePath>()

    while (untreatedLabels.isNotEmpty())
    {
        /*
            While the list of untreated labels is not empty, iteratively treat the last label. If feasible extensions
            are found, new labels are appended to the untreated labels list.
         */

        // Label to be treated
        val currentLabel: Label = untreatedLabels.last()

        /*
            Checking if the current label is already at the destination. If so, remove from the untreated labels list
            and select a new label to treat
         */

        if (currentLabel.currentNode == instance.destination)
        {
            // Current label does not need to be further extended, so remove from untreated label list.
            untreatedLabels.removeLast()

            // Finding the path corresponding to the current label
            val path = findPath(currentLabel, labelsMap)

            // Creating the FeasiblePath object and adding to the list of feasible paths.
            val feasiblePath = FeasiblePath(  path = path,
                                    totalPrize = currentLabel.collectedPrize,
                                    pathLength = currentLabel.pathLength)

            feasiblePathList.add(feasiblePath)

            continue
        }

        else
        {
            // Removing current label from untreated label list
            untreatedLabels.removeLast()

            /*
                Current label to be treated is not yet at the destination.
             */

            // List of edges with the current node of the current label being treated as the source.
            val outgoingEdges = graph.outgoingEdgesOf(currentLabel.currentNode)

            for (e in outgoingEdges)
            {
                val edgeWeight = graph.getEdgeWeight(e)
                val newPathLength = currentLabel.pathLength + edgeWeight
                val targetNode = graph.getEdgeTarget(e)

                if (targetNode !in currentLabel.visitedNodes)
                {
                    // Target node has not yet been visited by the vehicle along its path

                    // Checking if the new path hasn't exceeded the budget
                    if (newPathLength <= instance.budget)
                    {
                        // Extension to the target node is feasible. Creating a new label for this extension.
                        val newVisitedNodes = currentLabel.visitedNodes + listOf(targetNode)
                        val newCollectedPrize = currentLabel.collectedPrize + instance.scores[targetNode]
                        maxLabelIndex += 1

                        val extendedLabel = Label(  currentNode = targetNode,
                                                    collectedPrize = newCollectedPrize,
                                                    pathLength = newPathLength,
                                                    visitedNodes = newVisitedNodes,
                                                    labelIndex = maxLabelIndex,
                                                    predecessorIndex = currentLabel.labelIndex)

                        labelsMap[maxLabelIndex] = extendedLabel

                        // Adding the extended label to the list of untreated labels
                        untreatedLabels.add(extendedLabel)
                    }
                }
            }
        }
    }

    /*
        All feasible paths have been found.
     */

    return feasiblePathList.sortedWith(compareBy{it.totalPrize}).reversed()
}

fun findPath(label: Label, labelsMap: MutableMap<Int, Label>) : List<Int>{

    /*
        Backtracking from the current label until the initial label in order to construct the path taken.
     */

    var currentLabel = label

    val path = mutableListOf<Int>()

    /*
        Looping while the current label considered isn't the initial label.
     */
    while (currentLabel.predecessorIndex != -1)
    {
        path.add(currentLabel.currentNode)

        currentLabel = labelsMap[currentLabel.predecessorIndex]!!
    }

    // Adding the source node from the initial label
    path.add(currentLabel.currentNode)

    // Path is in reverse order, so reverse the list.
    path.reverse()

    return path
}


