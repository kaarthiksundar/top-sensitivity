package top.solver

import branchandbound.api.INode
import top.data.Route

data class TOPNode(
    val parent: TOPNode?,
    override val id: Long,
    override val parentLpObjective: Double = -Double.MAX_VALUE,
    override val lpFeasible: Boolean = false,
    override val lpIntegral: Boolean = false,
    override val lpObjective: Double = 0.0,
    val mustVisitVertices : IntArray = intArrayOf(),
    val mustVisitEdges : List<Pair<Int, Int>> = listOf(),
    val forbiddenVertices : IntArray = intArrayOf(),
    val forbiddenEdges : List<Pair<Int, Int>> = listOf(),
    val lpSolution : List<Pair<Route, Double>> = listOf(),
    val mipSolution : List<Route> = listOf(),
    override val mipObjective : Double? = null,
    val vertexReducedCosts : List<Double>? = null
) : INode {

    var children : List<TOPNode>? = null

    override fun toString() : String {
        val clauses = mutableListOf("ID = $id")

        if (parentLpObjective == Double.MAX_VALUE)
            clauses.add("parentLp = $parentLpObjective")
        else
            clauses.add("parentLp = %.2f".format(parentLpObjective))

        if (!lpFeasible)
            clauses.add("infeasible")

        return clauses.joinToString(", ", "Node(",")")
    }

    private fun getMIPObjective() : Double? {
        if (mipSolution.isEmpty())
            return null

        var obj = 0.0
        for (route in mipSolution) {
            obj += route.score
        }

        return obj
    }

}