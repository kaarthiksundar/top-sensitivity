package top.solver

import top.data.Parameters
import top.main.TOPException

/**
 * Class representing a partial path used in solving the elementary shortest path problem with
 * resource constraints in the pricing problem.
 *
 * @param vertex Current vertex
 * @param cost Accumulated reduced cost
 * @param score Accumulated prize collected
 * @param length Length of the path
 * @param parent Previous state that was extended to create this state. Null for source vertex.
 */
class State private constructor (
    val isForward: Boolean,
    val vertex: Int,
    val cost: Double,
    val score: Double,
    val length: Double,
    val parent: State?,
    val predecessor: Int,
    private val visitedCriticalVertices: LongArray,
    private val visitedGeneralVertices: LongArray,
    private val unreachableCriticalVertices: LongArray,
    val hasCycle : Boolean
) : Comparable<State>{

    /**
     * Reduced cost per unit length of partial path used for comparing states for sorting in a priority queue.
     */
    private val bangForBuck = if (length > 0) cost / length else 0.0

    var dominatingPredecessor : Int? = null

    /**
     * Create a new State object corresponding to extending the current State's path
     *
     * @param newVertex New vertex being visited in the extended path
     * @param edgeCost Reduced cost of edge used in the extension of the path
     * @param edgeLength Length of edge being used in the extension
     * @param newVertexScore Prize of new vertex being visited
     *
     * @return New state corresponding to the extended path
     */
    fun extend(
        newVertex: Int,
        edgeCost: Double,
        edgeLength: Double,
        newVertexScore: Double,
        isCritical: Boolean,
        parameters: Parameters
    ): State {
        val newVisitedCriticalVertices = visitedCriticalVertices.copyOf()
        // Only marking critical vertices
        if (isCritical)
            markVisited(newVertex, newVisitedCriticalVertices, parameters)

        val newVisitedGeneralVertices = visitedGeneralVertices.copyOf()

        if (!hasCycle) {
            // Current state does not already have a cycle present

            // Checking if extension to newVertex results in a cycle
            if (usedGeneralVertex(newVertex, parameters)) {

                // Extension to newVertex results in a cycle. Don't need to update visited vertices, so directly
                // return the new state with hasCycle set to true

                return State(
                    isForward,
                    vertex = newVertex,
                    cost = cost + edgeCost,
                    score = score + newVertexScore,
                    length = length + edgeLength,
                    parent = this,
                    predecessor = vertex,
                    visitedCriticalVertices = newVisitedCriticalVertices,
                    visitedGeneralVertices = newVisitedGeneralVertices,
                    unreachableCriticalVertices = unreachableCriticalVertices.copyOf(),
                    hasCycle = true
                )
            }
            else {
                // Extension to new vertex does not result in a cycle, i.e., newVertex has not yet been used.

                // Marking newVertex as visited and return a new state with hasCycle set to false
                markVisited(newVertex, newVisitedGeneralVertices, parameters)

                return State(
                    isForward,
                    vertex = newVertex,
                    cost = cost + edgeCost,
                    score = score + newVertexScore,
                    length = length + edgeLength,
                    parent = this,
                    predecessor = vertex,
                    visitedCriticalVertices = newVisitedCriticalVertices,
                    visitedGeneralVertices = newVisitedGeneralVertices,
                    unreachableCriticalVertices = unreachableCriticalVertices.copyOf(),
                    hasCycle = false
                )
            }
        }
        else {

            // State already has a cycle previously detected, so do not need to check for new cycles
            // Marking the newVertex as visited (even if it was already marked before, because there's no need
            // to check)
            markVisited(newVertex, visitedGeneralVertices, parameters)

            return State(
                isForward,
                vertex = newVertex,
                cost = cost + edgeCost,
                score = score + newVertexScore,
                length = length + edgeLength,
                parent = this,
                predecessor = vertex,
                visitedCriticalVertices = newVisitedCriticalVertices,
                visitedGeneralVertices = newVisitedGeneralVertices,
                unreachableCriticalVertices = unreachableCriticalVertices.copyOf(),
                hasCycle = true
            )

        }

        /*
        // Updating which vertices have been visited regardless if they are critical
        var newHasCycle = hasCycle
        val newVisitedGeneralVertices = visitedGeneralVertices.copyOf()
        if (!hasCycle && usedGeneralVertex(newVertex, parameters)) {
            // New vertex has already been previously used in this path, so mark the new state as having a cycle
            newHasCycle = true
        }
        else {
            // New vertex hasn't been previously used in the partial path. Mark this new vertex as used.
            markVisited(newVertex, newVisitedGeneralVertices, parameters)
        }



        return State(
            isForward,
            vertex = newVertex,
            cost = cost + edgeCost,
            score = score + newVertexScore,
            length = length + edgeLength,
            parent = this,
            predecessor = vertex,
            visitedCriticalVertices = newVisitedCriticalVertices,
            visitedGeneralVertices = newVisitedGeneralVertices,
            unreachableCriticalVertices = unreachableCriticalVertices.copyOf(),
            hasCycle = newHasCycle
        )

         */
    }

    override fun toString(): String {
        val typeStr = if (isForward) "forward" else "backward"
        return "State($typeStr,v=$vertex,l=$length,s=$score,r=$cost)"
    }

    /**
     * Function that returns the partial path corresponding to the label. The order the vertices are visited
     * is in most recently visited to first visited.
     */
    fun getPartialPath() : List<Int> {
        val path = mutableListOf<Int>()
        var state: State? = this
        while (state != null) {
            path.add(state.vertex)
            state = state.parent
        }
        return path
    }

    /**
     * Function that checks if this state and another given state share visited critical vertices.
     */
    fun hasCommonCriticalVisits(otherState: State) : Boolean {

        for (i in visitedCriticalVertices.indices) {

            // Checking the AND operation yields 0L (i.e., checking if a vertex is shared)
            if (visitedCriticalVertices[i] and otherState.visitedCriticalVertices[i] != 0L) {
                return true
            }
        }

        return false

    }

    fun hasCommonGeneralVisits(otherState: State) : Boolean {

        for (i in visitedGeneralVertices.indices) {
            if (visitedGeneralVertices[i] and otherState.visitedGeneralVertices[i] != 0L)
                return true
        }

        return false

    }

    fun markUnreachableCriticalVertex(vertex : Int, parameters : Parameters) {

        val quotient : Int = vertex / parameters.numBits
        val remainder : Int = vertex % parameters.numBits

        unreachableCriticalVertices[quotient] = unreachableCriticalVertices[quotient] or (1L shl remainder)
    }

    /**
     * Function that checks if this state dominates another given state.
     */
    fun dominates(otherState: State, parameters: Parameters, useVisitCondition: Boolean) : Boolean {

        // States can only be compared if they have a partial path ending at the same vertex
        if (vertex != otherState.vertex)
            throw TOPException("States must have same vertex to be comparable.")

        if (isForward != otherState.isForward)
            throw TOPException("States can only be compared if they are going in the same direction.")

        /**
         * Comparing the components of the state. Using a Boolean to track whether there's at least one strict
         * inequality.
         */
        var strict = false

        // Comparing the cost
        if (cost >= otherState.cost + parameters.eps)
            return false

        if (cost <= otherState.cost - parameters.eps)
            strict = true

        // Comparing the path length used
        if (length >= otherState.length + parameters.eps)
            return false

        if (length <= otherState.length - parameters.eps)
            strict = true

        // Checking visited vertices
        if (useVisitCondition) {

            for (i in visitedCriticalVertices.indices) {

                val thisCombined = visitedCriticalVertices[i] or unreachableCriticalVertices[i]
                val otherCombined = otherState.visitedCriticalVertices[i] or otherState.unreachableCriticalVertices[i]

                // Checking if the current state's path has "used" a critical vertex that the other state's path has
                // not "used", where "used" may mean either directly visited or has been made unreachable due to
                // the path taken
                if (thisCombined and otherCombined.inv() != 0L)
                    return false

                // Checking if the current state's set of critical vertices VISITED is a proper subset of the
                // other state's set of critical vertices. Here we use the visited critical vertices and so we
                // do not need an extra parameter that tracks the number of critical vertices that have been visited
                if (!strict && (visitedCriticalVertices[i].inv() and otherState.visitedCriticalVertices[i] != 0L))
                    strict = true
            }
        }

        return strict

    }

    private fun markVisited(vertex: Int, visitedVertices: LongArray, parameters: Parameters) {

        // Finding which set of n bits to update
        val quotient : Int = vertex / parameters.numBits

        // Finding which bit in the set of n bits to update
        val remainder : Int = vertex % parameters.numBits

        // Updating
        visitedVertices[quotient] = visitedVertices[quotient] or (1L shl remainder)

    }

    fun usedCriticalVertex(vertex: Int, parameters: Parameters) : Boolean {

        val quotient : Int = vertex / parameters.numBits
        val remainder : Int = vertex % parameters.numBits

        return visitedCriticalVertices[quotient] and (1L shl remainder) != 0L
    }

    private fun usedGeneralVertex(vertex: Int, parameters: Parameters) : Boolean {
        val quotient : Int = vertex / parameters.numBits
        val remainder : Int = vertex % parameters.numBits

        return visitedGeneralVertices[quotient] and (1L shl remainder) != 0L
    }

    companion object {

        /**
         * Factory constructor for creating the initial forward (backward) state at the source (destination)
         */
        fun buildTerminalState(isForward: Boolean, vertex: Int, numVertices: Int, parameters: Parameters) : State {

            val numberOfLongs : Int = (numVertices / parameters.numBits) + 1

            val arrayOfLongs = LongArray(numberOfLongs) {0L}

            // Updating the terminal vertex's bit to be a 1
            val quotient : Int = vertex / parameters.numBits
            val remainder : Int = vertex % parameters.numBits

            arrayOfLongs[quotient] = 1L shl remainder

            return State(
                isForward = isForward,
                vertex = vertex,
                cost = 0.0,
                score = 0.0,
                length = 0.0,
                parent = null,
                predecessor = -1,
                visitedCriticalVertices = arrayOfLongs,
                visitedGeneralVertices = arrayOfLongs,
                unreachableCriticalVertices = arrayOfLongs,
                hasCycle = false)
        }
    }

    /**
     * Comparator based on reduced cost per unit length.
     */
    override fun compareTo(other: State): Int {
        return when {
            bangForBuck < other.bangForBuck -> -1
            bangForBuck > other.bangForBuck -> 1
            else -> 0
        }
    }
}