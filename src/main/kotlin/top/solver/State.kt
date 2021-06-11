package top.solver

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

    companion object {

        /**
         * Factory constructor for creating the initial forward (backward) state at the source (destination)
         */
        fun buildTerminalState(isForward: Boolean, vertex: Int) : State {
            return State(isForward, vertex, 0.0, 0.0, 0.0, null, mutableListOf(vertex))
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