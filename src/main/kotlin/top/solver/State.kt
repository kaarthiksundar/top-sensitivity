package top.solver

import top.data.Route

/**
 * Class representing a label used in solving the elementary shortest path problem with resource constraints
 * in the pricing problem.
 *
 * @param vertex Current vertex
 * @param cost Accumulated reduced cost
 * @param score Accumulated prize collected
 * @param length Length of the path
 * @param parent Previous State object that was extended to create this State. Null if no predecessor.
 * @param visitedVertices List of vertices that have been visited along the path
 */
class State(val vertex: Int,
            val cost: Double,
            val score: Double,
            val length: Double,
            val parent: State?,
            val visitedVertices: MutableList<Int>){


    /**
     * Function for creating a new State object corresponding to extending the current State's path
     *
     * @param newVertex New vertex being visited in the extended path
     * @param edgeCost Reduced cost value associated with the edge used in the extension of the path
     * @param edgeLength Length of the edge being used in the extension
     * @param newVertexScore Prize associated with the new vertex being visited
     *
     * @return Returns a new State object corresponding to the extended path
     */
    fun extend(newVertex: Int,
               edgeCost: Double,
               edgeLength: Double,
               newVertexScore: Double) : State{

        val newVisitedVertices = visitedVertices.toMutableList()
        newVisitedVertices.add(newVertex)
        return State(
            vertex = newVertex,
            cost = cost + edgeCost,
            score = score + newVertexScore,
            length = length + edgeLength,
            parent = this,
            visitedVertices = newVisitedVertices)
    }


    /**
     * Function for creating the associated Route object for the path used when creating the current State object.
     *
     * @return Returns a [Route] object for the path used when creating the current State object.
     */
    fun generateRoute() : Route{
        val path = mutableListOf<Int>()
        var state : State? = this
        while(state!!.parent != null){
            path.add(state.vertex)
            state = state.parent
        }
        path.add(state.vertex)
        path.reverse()
        return Route(path, this.score, this.length)
    }
}