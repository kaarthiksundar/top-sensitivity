package top.solver

import top.data.Instance
import top.data.Parameters
import top.data.Route
import top.main.getEdgeWeight
import java.util.*
import kotlin.math.absoluteValue
import mu.KLogging
import top.main.SetGraph
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
     * Graph with forbidden vertices/edges removed.
     */
    private val reducedGraph: SetGraph,
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
    /**
     * List of [Route] objects containing admissible elementary routes to be added to the set cover model in the column
     * generation scheme.
     */
    private val elementaryRoutes = mutableListOf<Route>()

    /**
     * Tolerance used for comparing doubles
     */
    private val eps = parameters.eps

    /**
     * Number of vertices in the pricing problem is defined on for the current node
     */
    private val numVertices = instance.numVertices

    /**
     * Source vertex ID
     */
    private val source = instance.source

    /**
     * Destination vertex ID
     */
    private val destination = instance.destination

    /**
     * Maximum length for feasible paths
     */
    private val budget = instance.budget

    /**
     * Maximum number of elementary routes generated when solving the pricing problem
     */
    private val maxColumnsAdded = parameters.maxColumnsAdded

    /**
     * Priority Queue of forward states to be joined with non-dominated backward states and extended
     */
    private var unprocessedForwardStates = PriorityQueue<State>()

    /**
     * Priority Queue of backward states to be joined with non-dominated forward states and extended
     */
    private var unprocessedBackwardStates = PriorityQueue<State>()

    /**
     * List of lists of non-dominated forward states indexed by the vertex ID
     */
    private val nonDominatedForwardStates = List(numVertices) { mutableListOf<State>()}

    /**
     * List of lists of non-dominated backward states indexed by the vertex ID
     */
    private val nonDominatedBackwardStates = List(numVertices) { mutableListOf<State>()}

    /**
     * Current optimal route (not necessarily elementary) found
     */
    private var optimalRoute: Route? = null

    /**
     * Boolean Array indicating which vertices have been visited multiple times in the optimal solution found
     */
    private val isVisitedMultipleTimes = BooleanArray(numVertices) {false}

    /**
     * Boolean Array indicating which vertices are considered to be critical vertices and are therefore to be only
     * visited at most once
     */
    private val isCritical = BooleanArray(numVertices) {false}

    /**
     * Flag used to check if I-DSSR is to be exited and new duals are to be found.
     */
    private var stopSearch = false

    /**
     * Flag used to allow for a relaxed domination condition. False when the relaxation is used, true when the
     * standard domination conditions are used.
     */
    private var useVisitCondition = false

    /**
     * Function that performs I-DSSR to find elementary routes to add to the set cover model formulation in the RMP.
     *
     * @return List of [Route] objects containing admissible elementary routes to be added to the set cover model in the
     * column generation scheme.
     */
    fun generateColumns(): List<Route> {

        var searchIteration = 0

        do {

            // Using the same duals (i.e., same previous RMP solution), but the critical vertex set is modified
            logger.debug("Starting search iteration $searchIteration")

            // Clearing the forward/backward lists and updating critical vertex set
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
     * Function that updates the critical vertex set and resets the lists of non-dominated states. The optimal route
     * (not necessarily elementary) is also updated using the best-cached elementary route.
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
        }

        // Remaking initial states at source and destination
        nonDominatedForwardStates[source].add(State.buildTerminalState(isForward = true, vertex = source, numVertices = numVertices, parameters))
        nonDominatedBackwardStates[destination].add(State.buildTerminalState(isForward = false, vertex = destination, numVertices = numVertices, parameters))

        // Update optimal route with best cached elementary route
        if (optimalRoute != null && !optimalRoute!!.isElementary) {
            optimalRoute = elementaryRoutes.firstOrNull()
            for (route in elementaryRoutes.drop(1)) {
                if (route.reducedCost <= optimalRoute!!.reducedCost - eps)
                    optimalRoute = route
            }
        }
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
            for (e in reducedGraph.outgoingEdgesOf(currentVertex)) {

                val nextVertex = reducedGraph.getEdgeTarget(e)

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
            for (e in reducedGraph.incomingEdgesOf(currentVertex)) {

                val previousVertex = reducedGraph.getEdgeSource(e)

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

        // Admissible route found

        if (!(forwardState.hasCycle || backwardState.hasCycle) && !forwardState.hasCommonGeneralVisits(backwardState)) {
            // Path is elementary
            val newRoute = Route(
                path = forwardState.getPartialPath().asReversed() + backwardState.getPartialPath(),
                score = forwardState.score + backwardState.score,
                length = getJoinedPathLength(forwardState, backwardState),
                reducedCost = reducedCost,
                isElementary = true
            )

            elementaryRoutes.add(newRoute)

            // Updating the optimal route
            if (optimalRoute == null || reducedCost <= optimalRoute!!.reducedCost - eps)
                optimalRoute = newRoute
        }
        else {
            // Path is not elementary

            // Updating the optimal route
            if (optimalRoute == null || reducedCost <= optimalRoute!!.reducedCost - eps) {

                optimalRoute = Route(
                    path = forwardState.getPartialPath().asReversed() + backwardState.getPartialPath(),
                    score = forwardState.score + backwardState.score,
                    length = getJoinedPathLength(forwardState, backwardState),
                    reducedCost = reducedCost,
                    isElementary = false
                )
            }
        }

    }

    /**
     * Function that takes in a state (either forward or backward), checks if the path length does not exceed half the
     * budget and then performs the appropriate extension for that state.
     */
    private fun processState(state: State) {

        if (state.length >= budget / 2 - eps)
            return

        if (state.isForward) extendForward(state)
        else extendBackward(state)

    }

    /**
     * Function that checks if a join between a forward state and a backward state is feasible in the sense that the
     * resulting path does not visit a critical vertex more than once and the joined path's length does not exceed
     * the budget.
     */
    private fun isFeasibleJoin(forwardState: State, backwardState: State) : Boolean {
        return (!forwardState.hasCommonCriticalVisits(backwardState) &&
                getJoinedPathLength(forwardState, backwardState) <= budget)
    }

    /**
     * Function that returns the length of the resulting path from joining a given forward state and backward state. The
     * feasibility of this join is not checked in this function.
     */
    private fun getJoinedPathLength(forwardState: State, backwardState: State) : Double {
        return forwardState.length + backwardState.length + reducedGraph.getEdgeWeight(forwardState.vertex, backwardState.vertex)
    }

    /**
     * Function that finds all feasible extensions of a forward state and updates the lists of non-dominated
     * forward states and unprocessed forward states.
     */
    private fun extendForward(currentState: State) {
        // Vertex corresponding to partial path of forward state
        val currentVertex = currentState.vertex

        // Iterating over all possible extensions to neighboring vertices
        for (e in reducedGraph.outgoingEdgesOf(currentVertex)) {
            val newVertex = reducedGraph.getEdgeTarget(e)

            // Don't extend to critical vertices more than once
            if (currentState.usedCriticalVertex(newVertex, parameters))
                continue

            // No 2-cycles
            if (currentState.parent != null && currentState.parent.vertex == newVertex)
                continue

            // Checking if an extension is feasible
            val extension = extendIfFeasible(currentState, newVertex, reducedGraph.getEdgeWeight(e)) ?: continue

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
        for (e in reducedGraph.incomingEdgesOf(currentVertex)) {
            val newVertex = reducedGraph.getEdgeSource(e)

            // Don't extend to critical vertices more than once
            if (currentState.usedCriticalVertex(newVertex, parameters))
                continue

            // No 2-cycles
            if (currentState.parent != null && currentState.parent.vertex == newVertex)
                continue

            // Checking if an extension is feasible
            val extension = extendIfFeasible(currentState, newVertex, reducedGraph.getEdgeWeight(e)) ?: continue

            // Extension is feasible. Update unprocessed backward states
            addIfNonDominated(extension, nonDominatedBackwardStates[newVertex])
        }
    }

    /**
     * Function that performs an extension (either forward or backward) if it is feasible in the sense no critical
     * vertex is visited more than once and the budget is not exceeded.
     *
     * @return [State] resulting from a feasible extension or Null otherwise
     */
    private fun extendIfFeasible(currentState: State, newVertex: Int, edgeLength: Double) : State? {

        // Checking if the path length exceeds the budget
        if (currentState.length + edgeLength > budget)
            return null

        // Extension is feasible
        val edgeCost =
            if (currentState.isForward) vertexReducedCosts[newVertex] + edgeDuals[currentState.vertex][newVertex]
            else vertexReducedCosts[newVertex] + edgeDuals[newVertex][currentState.vertex]

        return currentState.extend(
            newVertex = newVertex,
            edgeCost = edgeCost,
            edgeLength = edgeLength,
            newVertexScore = instance.scores[newVertex],
            isCritical = isCritical[newVertex],
            parameters = parameters
        )
    }

    /**
     * Function that checks if the a newly created state is dominated before adding it to the list of unprocessed
     * states.
     */
    private fun addIfNonDominated(extension: State, existingStates: MutableList<State>) {

        // Updating unreachable critical vertices
        updateUnreachableVertices(extension)

        // Iterating over the existing states in reversed order. Iterating backwards leads to large speed improvements
        // when removing states in the list of existing non-dominated states since there is no concern of skipping
        // an element after removing
        if (parameters.useDomination) {
            for (i in existingStates.indices.reversed()) {
                if (existingStates[i].dominates(extension, parameters, useVisitCondition)) {

                    // Checking if 2-cycle domination criterion is satisfied
                    if (canRemoveDominated(existingStates[i], extension))
                        return
                }

                if (parameters.twoWayDomination)
                    if (extension.dominates(existingStates[i], parameters, useVisitCondition)) {
                        // Checking if 2-cycle domination criterion is satisfied
                        if (canRemoveDominated(extension, existingStates[i]))
                            existingStates.removeAt(i)
                    }
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

    private fun canRemoveDominated(dominating : State, dominated : State) : Boolean {

        // Checking if dominating state and dominated state have same predecessor
        if (dominating.predecessor == dominated.predecessor)
            return true

        // Paths have different predecessors.

        // Checking if at least one other dominating resource-feasible path has been found
        if (dominated.dominatingPredecessor == null) {
            // dominating state's path is the first resource-feasible path dominating the dominated state.
            // Store this information
            dominated.dominatingPredecessor = dominating.predecessor
            return false
        }
        else {
            // One previous resource-feasible path had been found already
            return dominated.dominatingPredecessor != dominating.predecessor
        }

    }

    private fun updateUnreachableVertices(state : State) {


        if (state.isForward) {

            // Checking neighbor vertices using outgoing edges to see if any vertex cannot be reached
            // within the path length budget using knowledge of the triangle inequality. Only apply
            // this check to critical vertices

            for (e in reducedGraph.outgoingEdgesOf(state.vertex)) {

                val targetVertex = reducedGraph.getEdgeTarget(e)

                if (isCritical[targetVertex] && state.length + reducedGraph.getEdgeWeight(e) > budget)
                    state.markUnreachableCriticalVertex(targetVertex, parameters)

            }
        }
        else {

            // Checking neighbor vertices using incoming edges to see if any vertex cannot be reached
            // within the path length budget using knowledge of the triangle inequality. Only apply
            // this check to critical vertices

            for (e in reducedGraph.incomingEdgesOf(state.vertex)) {

                val sourceVertex = reducedGraph.getEdgeSource(e)

                if (isCritical[sourceVertex] && state.length + reducedGraph.getEdgeWeight(e) > budget)
                    state.markUnreachableCriticalVertex(sourceVertex, parameters)
            }

        }

    }

    /**
     * Function that checks if two states satisfy the halfway condition.
     *
     * @return True if the halfway condition is satisfied and false otherwise
     */
    private fun halfway(forwardState: State, backwardState: State) : Boolean {

        val currDiff = (forwardState.length - backwardState.length).absoluteValue
        if (currDiff <= eps)
            return true

        val edgeLength = reducedGraph.getEdgeWeight(forwardState.vertex, backwardState.vertex)
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

        // Finding initial set of unprocessed states from the terminal states
        extendForward(State.buildTerminalState(isForward = true, vertex = source, numVertices = numVertices, parameters))
        extendBackward(State.buildTerminalState(isForward = false, vertex = destination, numVertices = numVertices, parameters))

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

