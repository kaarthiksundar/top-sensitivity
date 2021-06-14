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
 * @param visitedVertices List of vertices that have been visited along the path
 */
class State private constructor (
    val isForward: Boolean,
    val vertex: Int,
    val cost: Double,
    val score: Double,
    val length: Double,
    val parent: State?,
    val visitedVertices: MutableList<Int>
) : Comparable<State>{

    /**
     * Reduced cost per unit length of partial path used for comparing states for sorting in a priority queue.
     */
    private val bangForBuck = if (length > 0) cost / length else 0.0

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
        newVertexScore: Double
    ): State {
        val newVisitedVertices = visitedVertices.toMutableList()
        //newVisitedVertices.add(newVertex)
        newVisitedVertices[newVertex] += 1
        return State(
            isForward,
            vertex = newVertex,
            cost = cost + edgeCost,
            score = score + newVertexScore,
            length = length + edgeLength,
            parent = this,
            visitedVertices = newVisitedVertices
        )
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
     * Function that checks if this state and another given state share visited vertices. True if yes, false otherwise.
     */
    fun hasCommonVisits(otherState: State) : Boolean {

        for (i in visitedVertices.indices) {
            if (visitedVertices[i] and otherState.visitedVertices[i] != 0)
                return true
        }

        return false

    }

    /**
     * Function that checks if this state dominates another given state.
     */
    fun dominates(otherState: State, parameters: Parameters) : Boolean {

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
        for (i in visitedVertices.indices) {
            if (visitedVertices[i] > otherState.visitedVertices[i])
                return false

            if (visitedVertices[i] < otherState.visitedVertices[i])
                strict = true
        }

        return strict

    }

    companion object {

        /**
         * Factory constructor for creating the initial forward (backward) state at the source (destination)
         */
        fun buildTerminalState(isForward: Boolean, vertex: Int, numVertices: Int) : State {

            val visitedVertices = MutableList(size = numVertices){when (it) {vertex -> 1 else -> 0} }

            return State(isForward, vertex, 0.0, 0.0, 0.0, null, visitedVertices)
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