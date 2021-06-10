package top.solver

import top.data.Route

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
) {
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
        newVisitedVertices.add(newVertex)
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
     * Creating a route being tracked by the state.
     *
     * Vertices on the route are found by backtracking along [parent] states.
     *
     * @return [Route] object for the path used when creating the current state.
     */
    fun generateRoute(): Route {
        val path = mutableListOf<Int>()
        var state: State? = this
        while (state!!.parent != null) {
            path.add(state.vertex)
            state = state.parent
        }
        path.add(state.vertex)
        path.reverse()
        return Route(path, this.score, this.length)
    }

    companion object {

        /**
         * Factory constructor for creating the initial forward (backward) state at the source (destination)
         */
        fun buildTerminalState(isForward: Boolean, vertex: Int) : State {
            return State(isForward, vertex, 0.0, 0.0, 0.0, null, mutableListOf(vertex))
        }
    }
}