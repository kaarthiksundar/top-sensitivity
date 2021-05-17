package top.solver

import top.data.Instance
import top.main.SetGraph

data class Label(val currentNode: Int,
                 val collectedPrize: Double,
                 val pathLength: Double,
                 val visitedNodes: List<Int>,
                 val labelIndex: Int,
                 val predecessorIndex: Int)

data class FeasiblePath(val path: List<Int>, val totalPrize: Double, val pathLength: Double)


fun enumerateAllPathsWithinBudget(instance: Instance)
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
    val labelsMap = mutableMapOf<Int, Label>(0 to initialLabel)
    val labelsReachingDestination = mutableMapOf<Int, Label>()

    /*
        Mutable list of labels that need to be treated (i.e., checked for feasible extensions and then remove
        from the mutable list)
     */
    val untreatedLabels = mutableListOf(initialLabel)

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
            untreatedLabels.removeLast()
            labelsReachingDestination[currentLabel.labelIndex] = currentLabel
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
        All feasible paths have been found. Creating a list of FeasiblePath objects.
     */

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


