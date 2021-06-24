package top.solver

import top.data.Instance
import top.data.Parameters
import top.data.Route
import top.main.getEdgeWeight
import java.util.*
import kotlin.math.absoluteValue

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
    private val unprocessedBackwardStates = PriorityQueue<State>()

    private val nonDominatedForwardStates = List(instance.numVertices) { mutableListOf<State>()}
    private val nonDominatedBackwardStates = List(instance.numVertices) { mutableListOf<State>()}

    private val elementaryRoutes = mutableListOf<Route>()

    private val graph = instance.graph

    private val eps = parameters.eps

    private val maxColumnsAdded = parameters.maxColumnsAdded

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

        bidirectional()

        return elementaryRoutes
    }

    private fun performAllJoins(currentState: State) {
        val currentVertex = currentState.vertex

        // Joining forward state with all non-dominated backward states
        if (currentState.isForward) {
            for (e in graph.outgoingEdgesOf(currentVertex)) {

                val nextVertex = graph.getEdgeTarget(e)

                for (backwardState in nonDominatedBackwardStates[nextVertex]) {

                    // Attempting to join
                    join(currentState, backwardState)

                    // Checking if max number of elementary routes reached
                    if (elementaryRoutes.size >= maxColumnsAdded)
                        return
                }

            }
        }
        else { // Joining backward state with all non-dominated forward states
            for (e in graph.incomingEdgesOf(currentVertex)) {

                val previousVertex = graph.getEdgeSource(e)

                for (forwardState in nonDominatedForwardStates[previousVertex]) {

                    // Attempting to join
                    join(forwardState, currentState)

                    // Checking if max number of elementary routes reached
                    if (elementaryRoutes.size >= maxColumnsAdded)
                        return
                }
            }
        }
    }

    private fun join(forwardState: State, backwardState: State) {

        // Checking if the join is feasible
        if (!isFeasibleJoin(forwardState, backwardState) || !halfway(forwardState, backwardState))
            return

        // Finding the reduced cost of the joined path
        val reducedCost = vehicleCoverDual + forwardState.cost + backwardState.cost + edgeDuals[forwardState.vertex][backwardState.vertex]

        // Checking if the reduced cost is negative
        if (reducedCost >= - eps)
            return

        // Elementary path with negative reduced cost found. Storing it
        val joinedPath = mutableListOf<Int>()
        joinedPath.addAll(forwardState.getPartialPath().asReversed())
        joinedPath.addAll(backwardState.getPartialPath())

        val edgeLength = graph.getEdgeWeight(forwardState.vertex, backwardState.vertex)

        val newElementaryRoute = Route(
            path = joinedPath,
            score = forwardState.score + backwardState.score,
            length = forwardState.length + edgeLength + backwardState.length
        )

        elementaryRoutes.add(newElementaryRoute)

    }

    private fun processState(state: State) {

        if (state.length >= budget / 2 + eps)
            return

        if (state.isForward) extendForward(state)
        else extendBackward(state)

    }

    /**
     * Function that checks if joining a forward state with a backward state yields an elementary path with a length
     * not exceeding the given budget.
     */
    private fun isFeasibleJoin(forwardState: State, backwardState: State) : Boolean {

        val edgeLength = graph.getEdgeWeight(forwardState.vertex, backwardState.vertex)
        val joinedPathLength = forwardState.length + edgeLength + backwardState.length

        return (!forwardState.hasCommonVisits(backwardState) && joinedPathLength <= budget)

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
            if (parameters.useDomination)
                addIfNonDominated(extension, nonDominatedForwardStates[newVertex])
            else
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
            addIfNonDominated(extension, nonDominatedBackwardStates[newVertex])
        }
    }

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
        if (extension.isForward)
            unprocessedForwardStates.add(extension)
        else
            unprocessedBackwardStates.add(extension)

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

    private fun halfway(forwardState: State, backwardState: State) : Boolean {

        val currDiff = (forwardState.length - backwardState.length).absoluteValue
        if (currDiff <= eps)
            return true

        val edgeLength = graph.getEdgeWeight(forwardState.vertex, backwardState.vertex)
        var otherDiff = 0.0

        if (forwardState.length <= backwardState.length - eps) {
            if (backwardState.parent != null) {
                otherDiff = (forwardState.length + edgeLength - backwardState.parent.length).absoluteValue
            }
        }
        else if (forwardState.parent != null) {
            otherDiff = (forwardState.parent.length - (edgeLength + backwardState.length)).absoluteValue
        }

        if (currDiff <= otherDiff - eps)
            return true

        if (currDiff >= otherDiff + eps)
            return false

        return forwardState.length >= backwardState.length + eps

    }

    private fun bidirectional() : MutableList<Route> {

        // Initializing the forward and backward states at the terminal vertices
        unprocessedForwardStates.add(State.buildTerminalState(isForward = true, vertex = source, numVertices = instance.numVertices, parameters))
        unprocessedBackwardStates.add(State.buildTerminalState(isForward = false, vertex = destination, numVertices = instance.numVertices, parameters))

        // Flag for which side to be extended
        var processForward = true

        while (unprocessedForwardStates.isNotEmpty() || unprocessedBackwardStates.isNotEmpty()) {

            // State to be extended
            var state: State? = null

            if (processForward) {
                if (unprocessedForwardStates.isNotEmpty()) {
                    //state = unprocessedForwardStates.removeLast()
                    state = unprocessedForwardStates.remove()
                }
            }
            else {
                if(unprocessedBackwardStates.isNotEmpty()) {
                    //state = unprocessedBackwardStates.removeLast()
                    state = unprocessedBackwardStates.remove()
                }
            }

            // Switching sides
            processForward = !processForward

            // Switching sides if one side has no states to extend
            if (state == null)
                continue

            // Finding all possible joins
            performAllJoins(state)

            if (elementaryRoutes.size >= maxColumnsAdded)
                return elementaryRoutes

            // Max number of elementary routes not yet found, so extend the state
            processState(state)

        }

        return elementaryRoutes

    }

}

