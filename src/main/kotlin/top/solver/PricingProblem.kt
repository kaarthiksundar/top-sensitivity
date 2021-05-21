package top.solver

import top.data.Instance
import top.data.Parameters
import top.data.Route
import top.main.SetGraph

/**
 * Class responsible for handling the pricing problem in the column generation scheme.
 *
 * @param instance [Instance] object containing information about the problem instance
 * @param vehicleCoverDual Dual variable associated with the constraint that at most m vehicles are used
 * @param vertexReducedCosts List of reduced costs resulting from the dual variables after solving the restricted
 * master problem
 * @param parameters [Parameters] object containing relevant information for the solver
 */
class PricingProblem(
    private val instance: Instance,
    private val vehicleCoverDual: Double,
    private val vertexReducedCosts: List<Double>,
    private val parameters: Parameters
) {

    /**
     * Function that solves the pricing problem (elementary shortest path problem with resource constraints)
     *
     * @return List of [Route] objects to be added to the restricted master problem in the column generation scheme
     */
    fun generateColumns() : List<Route>{
        /**
         * The pricing problem is as follows.
         *
         * Given the dual variables and budget, we wish to find an elementary shortest paths from the source to the
         * destination with a path length less than the budget and a negative accumulated cost.
         *
         * This will be done using a standard labeling algorithm. This procedure could be improved by including
         * domination rules and other acceleration techniques (such as unreachable nodes)
         */

        val graph: SetGraph = instance.graph

        val initialState = State(instance.source, vehicleCoverDual,0.0,0.0,null, mutableListOf(instance.source))

        val unprocessedStates = mutableListOf(initialState)
        val newRoutes = mutableListOf<Route>()

        while (unprocessedStates.isNotEmpty()){

            val currentState = unprocessedStates.last()
            unprocessedStates.removeLast()

            if (currentState.vertex == instance.destination && currentState.cost < 0){
                newRoutes.add(currentState.generateRoute())

                if (newRoutes.size >= parameters.maxColumnsAdded)
                    return newRoutes
            }
            else{
                val outgoingEdges = graph.outgoingEdgesOf(currentState.vertex)
                for (e in outgoingEdges){
                    val edgeLength = graph.getEdgeWeight(e)
                    val newLength = currentState.length + edgeLength
                    val newVertex = graph.getEdgeTarget(e)
                    if (newVertex in currentState.visitedVertices || newLength > instance.budget)
                        continue

                    // Cost of the edge is the vertexReducedCost at currentState.vertex
                    val edgeCost = vertexReducedCosts[currentState.vertex]
                    val newState = currentState.extend(newVertex, edgeCost, edgeLength, instance.scores[newVertex])
                    unprocessedStates.add(newState)
                }
            }
        }

        return newRoutes
    }



}