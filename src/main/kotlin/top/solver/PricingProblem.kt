package top.solver

import top.data.Instance
import top.data.Parameters
import top.data.Route
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

    private val unprocessedForwardStates = PriorityQueue<State>()
    private val nonDominatedForwardStates = List(instance.numVertices) { mutableListOf<State>()}

    private val elementaryRoutes = mutableListOf<Route>()

    private val graph = instance.graph

    private val eps = parameters.eps

    private val maxColumnsAdded = parameters.maxColumnsAdded

    private val source = instance.source
    private val destination = instance.destination
    private val budget = instance.budget

    /**
     * Solve the pricing problem (elementary shortest path problem with resource constraints)
     *
     * Given the dual variables and budget, we wish to find elementary shortest paths from the
     * source to the destination with a path length less than the budget and a negative
     * accumulated cost.
     *
     * @return routes to be added to the restricted master problem in the column generation scheme
     */
    fun generateColumns(): List<Route> {

        forwardLabelingOnly()

        return elementaryRoutes
    }


    /**
     * Function for processing a forward state.
     */
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
            addIfNonDominated(extension, nonDominatedForwardStates[newVertex])

        }
    }

    /**
     * Function that extends the given State object if feasible in the sense the resulting path is elementary
     * and its length does not exceed the budget.
     */
    private fun extendIfFeasible(currentState: State, newVertex: Int, edgeLength: Double) : State? {

        // Length of partial path after extension
        val newPathLength = currentState.length + edgeLength

        // Checking if new path is elementary or if the path length exceeds the budget
        if (currentState.inPartialPath(newVertex, parameters) || newPathLength > budget)
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
            newVertexScore = newVertexScore,
            parameters
        )
    }

    /**
     * Function that checks if the a newly created state is dominated before adding it to the list of unprocessed
     * states.
     */
    private fun addIfNonDominated(extension: State, existingStates: MutableList<State>) {

        // Marking all unreachable nodes before checking for dominance
        updateUnreachableVertices(extension)

        // Iterating over the existing states in reversed order. Iterating backwards leads to large speed improvements
        // when removing states in the list of existing non-dominated states
        if (parameters.useDomination) {
            for (i in existingStates.indices.reversed()) {
                if (existingStates[i].dominates(extension, parameters))
                    return
                if (parameters.twoWayDomination)
                    if (extension.dominates(existingStates[i], parameters))
                        existingStates.removeAt(i)
            }
        }

        // Current state is not dominated by previously found non-dominated states. Add to list of non-dominated states
        existingStates.add(extension)

        // Updating the unprocessed states
        unprocessedForwardStates.add(extension)

    }

    /**
     * Function that identifies all vertices that are unreachable for a given state in the sense that the time the
     * vehicle reaches such a vertex will exceed the given budget.
     */
    private fun updateUnreachableVertices(state: State) {

        val currentVertex = state.vertex

        for (e in graph.outgoingEdgesOf(currentVertex)) {

            val targetVertex = graph.getEdgeTarget(e)
            val edgeLength = graph.getEdgeWeight(e)

            if (state.length + edgeLength > budget)
                state.markVertex(targetVertex, state.visitedVertices, parameters)
        }
    }

    /**
     * Function that performs forward labeling only to solve the pricing problem.
     */
    private fun forwardLabelingOnly() {

        // Initial (forward) state at the source
        unprocessedForwardStates.add(State.buildTerminalState(isForward = true, vertex = source, numVertices = instance.numVertices, parameters))

        while (unprocessedForwardStates.isNotEmpty()) {

            val currentState = unprocessedForwardStates.remove()

            // Checking if destination has been reached and if so, if the elementary route has negative reduced cost
            if (currentState.vertex == destination) {
                if (currentState.cost + vehicleCoverDual < - eps) {
                    elementaryRoutes.add(Route(currentState.getPartialPath().asReversed(), currentState.score, currentState.length))

                    // Checking if maximum number of elementary routes reached
                    if (elementaryRoutes.size >= maxColumnsAdded)
                        return
                }
            }
            else
                extendForward(currentState)
        }
    }
}

