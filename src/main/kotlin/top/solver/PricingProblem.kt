package top.solver

import top.data.Instance
import top.data.Parameters
import top.data.Route
import top.main.getEdgeWeight
import java.util.*
import kotlin.math.absoluteValue
import mu.KLogging
import top.main.TOPException

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
    private val elementaryRoutes = mutableListOf<Route>()

    private val graph = instance.graph

    private val eps = parameters.eps

    private val numVertices = instance.numVertices

    private val maxColumnsAdded = parameters.maxColumnsAdded

    private var unprocessedForwardStates = PriorityQueue<State>()
    private var unprocessedBackwardStates = PriorityQueue<State>()

    private val nonDominatedForwardStates = List(numVertices) { mutableListOf<State>()}
    private val nonDominatedBackwardStates = List(numVertices) { mutableListOf<State>()}

    private var optimalRoute: Route? = null

    private val isVisitedMultipleTimes = BooleanArray(numVertices) {false}
    private val isCritical = BooleanArray(numVertices) {false}

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

    private var stopSearch = false
    private var useVisitCondition = false

    /**
     * Function that performs I-DSSR to find elementary routes to add to the set cover model formulation in the RMP.
     */
    fun generateColumns(): List<Route> {

        // Initializing the (non-dominated) states at the source and destination
        nonDominatedForwardStates[source].add(State.buildTerminalState(isForward = true, vertex = source, numVertices = numVertices, parameters))
        nonDominatedBackwardStates[destination].add(State.buildTerminalState(isForward = false, vertex = destination, numVertices = numVertices, parameters))

        var searchIteration = 0

        do {

            // Using the same duals (i.e., same previous RMP solution), but the critical vertex set is modified
            logger.debug("Starting search iteration $searchIteration")

            // Clearing the forward/backward lists
            initializeIteration()

            // With the current critical vertex set, find all admissible elementary routes
            interleavedSearch()

            // Check if number of admissible elementary routes exceeds a set amount. If so, resolve RMP for new duals
            if (elementaryRoutes.size >= maxColumnsAdded) {
                logger.debug("DSSR reset due to max number of elementary routes found")
                break
            }

            // Check if number of admissible elementary routes exceeds a minimum amount. If so, resolve RMP
            if (elementaryRoutes.size >= parameters.maxPathsAfterSearch) {
                logger.debug("DSSR reset due to elementary route existence")
                break
            }

            // Otherwise, from the best route find which vertices visited multiple times and update critical vertex set
            multipleVisits()

            // Change to stricter domination if no optimal route found
            if (optimalRoute == null && !useVisitCondition) {
                useVisitCondition = true
                logger.debug("Repeating column search with stricter dominance checking")
                searchIteration++
                continue
            }

            // Checking if the optimal solution has multiple visits. If not, resolve RMP for new duals
            stopSearch = isVisitedMultipleTimes.none{it}
            logger.debug("End search iteration $searchIteration, stop: $stopSearch")
            searchIteration++

        } while(!stopSearch)

        return elementaryRoutes
    }

    /**
     * Function that updates the critical vertex set and resets the lists of non-dominated states and unprocessed
     * states
     */
    private fun initializeIteration() {
        // Updating the critical vertices
        for (i in isCritical.indices) {
            if (!isCritical[i]) {
                // If vertex is not initially critical, check if it was visited multiple times in previous iteration
                isCritical[i] = isVisitedMultipleTimes[i]
            }
        }

        // Clearing all states
        for (i in nonDominatedForwardStates.indices) {
            nonDominatedForwardStates[i].clear()
            nonDominatedBackwardStates[i].clear()

            unprocessedForwardStates.clear()
            unprocessedBackwardStates.clear()
        }

        // Remaking initial states at source and destination
        nonDominatedForwardStates[source].add(State.buildTerminalState(isForward = true, vertex = source, numVertices = numVertices, parameters))
        nonDominatedBackwardStates[destination].add(State.buildTerminalState(isForward = false, vertex = destination, numVertices = numVertices, parameters))

        // Update optimal route with best cached elementary route
        if (optimalRoute != null && hasCycle(optimalRoute!!.path)) {
            optimalRoute = elementaryRoutes.firstOrNull()
            for (route in elementaryRoutes.drop(1)) {
                if (route.reducedCost <= optimalRoute!!.reducedCost - eps)
                    optimalRoute = route
            }
        }
    }

    /**
     * Function that checks if a given path has a cycle.
     */
    private fun hasCycle(path: List<Int>) : Boolean {
        val visited = hashSetOf<Int>()
        for (vertex in path) {
            if (visited.contains(vertex))
                return true
            visited.add(vertex)
        }
        return false
    }

    /**
     * Function that identifies which vertices in the optimal path (not necessarily elementary) were visited multiple
     * times and marks these vertices as critical vertices for the next iteration of DSSR.
     */
    private fun multipleVisits() {
        // Resetting the Boolean array tracking which vertices in the optimal route are visited multiple times
        isVisitedMultipleTimes.fill(false)
        val optimalPath = optimalRoute?.path ?: return

        val numVisits = IntArray(numVertices) { 0 }
        // Tracking how many times each vertex has been visited
        for (vertex in optimalPath) {
            numVisits[vertex]++
            if (numVisits[vertex] > 1) {
                isVisitedMultipleTimes[vertex] = true
                // Checking that critical vertices are visited at most once
                if (isCritical[vertex]) {
                    logger.error("Multiple visits to critical vertex $vertex")
                    logger.error("Problematic route: $optimalRoute")
                    throw TOPException("Cycles with critical vertices")
                }
            }
        }
        val multipleVisits = (0 until numVertices).filter { isVisitedMultipleTimes[it] }
        logger.debug("Current multiple visits: $multipleVisits")
    }

    /**
     * Function that takes a forward (backward) state and joins it with all feasible backward (forward) non-dominated
     * states
     */
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

    /**
     * Function that joins two states to produce a complete path from the source to the destination. If the resulting
     * path is an admissible elementary route, it is added to the list of elementary routes found.
     */
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

        val newRoute = Route(
            path = joinedPath,
            score = forwardState.score + backwardState.score,
            length = forwardState.length + edgeLength + backwardState.length,
            reducedCost = reducedCost
        )

        // Updating the optimal route
        if (optimalRoute == null || reducedCost <= optimalRoute!!.reducedCost - eps)
            optimalRoute = newRoute

        // Checking if the new route is an elementary route
        if (!hasCycle(joinedPath))
            elementaryRoutes.add(newRoute)

    }

    /**
     * Function that takes in a state (either forward or backward), checks if the path length does not exceed half the
     * budget and then performs the appropriate extension for that state.
     */
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

    /**
     * Function that finds all feasible extensions of a forward state and updates the lists of non-dominated
     * forward states and unprocessed forward states.
     */
    private fun extendForward(currentState: State) {
        // Vertex corresponding to partial path of forward state
        val currentVertex = currentState.vertex

        // Iterating over all possible extensions to neighboring vertices
        for (e in graph.outgoingEdgesOf(currentVertex)) {
            val edgeLength = graph.getEdgeWeight(e)
            val newVertex = graph.getEdgeTarget(e)

            // Don't extend to critical vertices more than once
            if (currentState.inPartialPath(newVertex, parameters))
                continue

            // No 2-cycles
            if (currentState.parent != null && currentState.parent.vertex == newVertex)
                continue

            // Checking if an extension is feasible
            val extension = extendIfFeasible(currentState, newVertex, edgeLength) ?: continue

            // Extension is feasible. Update unprocessed forward states
            addIfNonDominated(extension, nonDominatedForwardStates[newVertex])
        }
    }

    /**
     * Function that finds all feasible extensions of a backward state and updates the lists of non-dominated
     * backward states and unprocessed backward states.
     */
    private fun extendBackward(currentState: State) {
        // Vertex corresponding to partial path of backward state
        val currentVertex = currentState.vertex

        // Iterating over all possible extensions using incoming edges
        for (e in graph.incomingEdgesOf(currentVertex)) {
            val edgeLength = graph.getEdgeWeight(e)
            val newVertex = graph.getEdgeSource(e)

            // Don't extend to critical vertices more than once
            if (currentState.inPartialPath(newVertex, parameters))
                continue

            // No 2-cycles
            if (currentState.parent != null && currentState.parent.vertex == newVertex)
                continue

            // Checking if an extension is feasible
            val extension = extendIfFeasible(currentState, newVertex, edgeLength) ?: continue

            // Extension is feasible. Update unprocessed backward states
            addIfNonDominated(extension, nonDominatedBackwardStates[newVertex])
        }
    }

    /**
     * Function that performs an extension (either forward or backward) if it is feasible in the sense no critical
     * vertex is visited more than once and the budget is not exceeded.
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
            isCritical = isCritical[newVertex],
            parameters = parameters
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
                if (existingStates[i].dominates(extension, parameters, useVisitCondition))
                    return
                if (parameters.twoWayDomination)
                    if (extension.dominates(existingStates[i], parameters, useVisitCondition))
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

        if (state.isForward) {
            for (e in graph.outgoingEdgesOf(currentVertex)) {

                val targetVertex = graph.getEdgeTarget(e)
                val edgeLength = graph.getEdgeWeight(e)

                if (isCritical[targetVertex] && state.length + edgeLength > budget)
                    state.markUnreachable(targetVertex, parameters)

            }
        }
        else
        {
            for (e in graph.incomingEdgesOf(currentVertex)) {

                val targetVertex = graph.getEdgeSource(e)
                val edgeLength = graph.getEdgeWeight(e)

                if (isCritical[targetVertex] && state.length + edgeLength > budget)
                    state.markUnreachable(targetVertex, parameters)

            }
        }

    }

    /**
     * Function that checks if two states satisfy the halfway condition.
     */
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

    /**
     * Function that runs the actual interleaved search algorithm to find admissible elementary routes.
     */
    private fun interleavedSearch() {

        // Initializing the forward and backward states at the terminal vertices
        unprocessedForwardStates.add(State.buildTerminalState(isForward = true, vertex = source, numVertices = numVertices, parameters))
        unprocessedBackwardStates.add(State.buildTerminalState(isForward = false, vertex = destination, numVertices = numVertices, parameters))

        nonDominatedForwardStates[source].add(State.buildTerminalState(isForward = true, vertex = source, numVertices = numVertices, parameters))
        nonDominatedBackwardStates[destination].add(State.buildTerminalState(isForward = false, vertex = destination, numVertices = numVertices, parameters))

        // Flag for which side to be extended
        var processForward = true

        while (unprocessedForwardStates.isNotEmpty() || unprocessedBackwardStates.isNotEmpty()) {

            // State to be extended
            var state: State? = null

            if (processForward) {
                if (unprocessedForwardStates.isNotEmpty()) {
                    state = unprocessedForwardStates.remove()
                }
            }
            else {
                if(unprocessedBackwardStates.isNotEmpty()) {
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

            if (elementaryRoutes.size >= maxColumnsAdded) {
                stopSearch = true
                return
            }

            // Max number of elementary routes not yet found, so extend the state
            processState(state)

        }
    }

    companion object : KLogging()

}

