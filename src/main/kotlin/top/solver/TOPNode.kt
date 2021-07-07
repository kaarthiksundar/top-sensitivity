package top.solver

import branchandbound.api.INode

data class TOPNode(
    override val id: Long,
    override val parentLpObjective: Double = Double.MAX_VALUE,
    override val lpFeasible: Boolean = false,
    override val lpIntegral: Boolean = false,
    override val lpObjective: Double = 0.0,
    val mustVisitVertices : IntArray = intArrayOf(),
    val mustVisitEdges : List<Pair<Int, Int>> = listOf(),
    val forbiddenVertices : IntArray = intArrayOf(),
    val forbiddenEdges : List<Pair<Int, Int>> = listOf()
) : INode {

    override val mipObjective : Double? = null

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

}