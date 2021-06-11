package top.solver

import org.jgrapht.Graphs
import top.data.Instance
import top.data.Parameters
import top.data.Route
import top.main.SetGraph
import top.main.getEdgeWeight
import java.util.*

/**
 * Class responsible for handling the pricing problem in the column generation scheme.
 *
 * @param parameters [Parameters]
 */
class PricingProblem(
    /**
     * Object with all routing problem data.
     */
    private val instance: Instance,
    /**
     * Dual variable associated with the constraint that at most m vehicles are used.
     */
    private val vehicleCoverDual: Double,
    /**
     * Reduced costs based on dual solution of restricted master problem
     */
    private val vertexReducedCosts: List<Double>,

    /**
     * 2D list of duals for enforced edges.
     */
    private val edgeDuals : List<MutableList<Double>>,

    /**
     * App configuration that includes solve parameters.
     */
    private val parameters: Parameters


) {

    private val unprocessedForwardStates = mutableListOf<State>()
    private val unprocessedBackwardStates = mutableListOf<State>()

    private val elementaryRoutes = mutableListOf<Route>()

    private val graph = instance.graph

    /**
     * Solve the pricing problem (elementary shortest path problem with resource constraints)
     *
     * Given the dual variables and budget, we wish to find elementary shortest paths from the
     * source to the destination with a path length less than the budget and a negative
     * accumulated cost.
     *
     * This will be done using a standard labeling algorithm. This procedure could be improved
     * by including domination rules and other acceleration techniques
     * (such as unreachable nodes).
     *
     * @return routes to be added to the restricted master problem in the column generation scheme
     */

    private val source = instance.source
    private val destination = instance.destination
    private val budget = instance.budget

    fun generateColumns(): List<Route> {

        if (parameters.forwardOnly)
            forwardLabelingOnly()

        if (parameters.backwardOnly)
            backwardLabelingOnly()

        return elementaryRoutes
    }

    private fun extendForward(currentState: State) {
        // Vertex corresponding to partial path of forward state
        val currentVertex = currentState.vertex

        // Iterating over all possible extensions to neighboring vertices
        for (e in graph.outgoingEdgesOf(currentVertex)) {
            val edgeLength = graph.getEdgeWeight(e)
            val newVertex = graph.getEdgeTarget(e)

            // Checking if an extension is feasible
            val extension = extendIfFeasible(currentState, newVertex, edgeLength) ?: continue

            // Extension is feasible. Update unprocessed forward states
            unprocessedForwardStates.add(extension)
        }
    }

    private fun extendBackward(currentState: State) {
        // Vertex corresponding to partial path of backward state
        val currentVertex = currentState.vertex

        // Iterating over all possible extensions using incoming edges
        for (e in graph.incomingEdgesOf(currentVertex)) {
            val edgeLength = graph.getEdgeWeight(e)
            val newVertex = graph.getEdgeSource(e)

            // Checking if an extension is feasible
            val extension = extendIfFeasible(currentState, newVertex, edgeLength) ?: continue

            // Extension is feasible. Update unprocessed backward states
            unprocessedBackwardStates.add(extension)
        }
    }

    private fun extendIfFeasible(currentState: State, newVertex: Int, edgeLength: Double) : State? {

        // Length of partial path after extension
        val newPathLength = currentState.length + edgeLength

        // Checking if new path is elementary or if the path length exceeds the budget
        if (newVertex in currentState.visitedVertices || newPathLength > budget)
            return null

        // Extension is feasible
        var edgeCost = vertexReducedCosts[newVertex]
        edgeCost +=
            if (currentState.isForward) {edgeDuals[currentState.vertex][newVertex]}
            else {edgeDuals[newVertex][currentState.vertex]}
        val newVertexScore = instance.scores[newVertex]

        return currentState.extend(
            newVertex = newVertex,
            edgeCost = edgeCost,
            edgeLength = edgeLength,
            newVertexScore = newVertexScore
        )
    }

    /**
     * Function that performs forward labeling only to solve the pricing problem.
     */
    private fun forwardLabelingOnly() {

        // Initial (forward) state at the source
        unprocessedForwardStates.add(State.buildTerminalState(isForward = true, vertex = source))

        while (unprocessedForwardStates.isNotEmpty()) {

            //val currentState = unprocessedForwardStates.remove()
            val currentState = unprocessedForwardStates.removeLast()

            // Checking if destination has been reached and if so, if the elementary route has negative reduced cost
            if (currentState.vertex == destination) {
                if (currentState.cost + vehicleCoverDual < - parameters.eps) {
                    elementaryRoutes.add(Route(currentState.getPartialPath().reversed(), currentState.score, currentState.length))

                    // Checking if maximum number of elementary routes reached
                    if (elementaryRoutes.size >= parameters.maxColumnsAdded)
                        return
                }
            }
            else
                extendForward(currentState)
        }
    }

    /**
     * Function that performs backward labeling only to solve the pricing problem.
     */
    private fun backwardLabelingOnly() {

        // Initial (backward) state at the destination
        unprocessedBackwardStates.add(State.buildTerminalState(isForward = false, vertex = destination))

        while (unprocessedBackwardStates.isNotEmpty()) {

            val currentState = unprocessedBackwardStates.removeLast()

            // Checking if the source has been reached and if so, if the elementary route has negative reduced cost
            if (currentState.vertex == source) {
                if (currentState.cost + vehicleCoverDual < - parameters.eps) {
                    elementaryRoutes.add(Route(currentState.getPartialPath(), currentState.score, currentState.length))

                    if (elementaryRoutes.size >= parameters.maxColumnsAdded)
                        return
                }
            }
            else
                extendBackward(currentState)
        }
    }

}

