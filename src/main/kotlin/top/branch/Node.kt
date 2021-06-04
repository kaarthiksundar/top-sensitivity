package top.branch

import top.Util.EPS

data class Node(
    /**
     * Unique node identifier used to break ties during comparisons with the assumption that
     * smaller ids are created earlier.
     */
    private val id: Int,
    /**
     * Keys are variable ids. Values are integers to which variables are locked.
     */
    val restrictions: Map<Int, Int> = mapOf(),
    /**
     * LP objective of node whose branching creates the current node.
     */
    val parentLpObjective: Double = Double.MAX_VALUE,
    val lpFeasible: Boolean = false,
    val lpIntegral: Boolean = false,
    val lpObjective: Double = Double.MAX_VALUE,
    val lpSolution: Map<Int, Double> = mapOf()
) : Comparable<Node> {
    /**
     * Useful to build a min-priority queue of nodes. Assumes that problem solved is a maximization
     * problem.
     */
    override fun compareTo(other: Node): Int = when {
        parentLpObjective <= other.parentLpObjective - EPS -> -1
        parentLpObjective >= other.parentLpObjective + EPS -> 1
        else -> id.compareTo(other.id)
    }

    override fun toString(): String {
        val clauses = mutableListOf("id=$id")
        if (lpFeasible) {
            if (parentLpObjective == Double.MAX_VALUE)
                clauses.add("parentLp=$parentLpObjective")
            else
                clauses.add("parentLp=%.2f".format(parentLpObjective))
            clauses.add("lp=%.2f".format(lpObjective))
            clauses.add(if (lpIntegral) "integral" else "fractional")
        } else clauses.add("infeasible")

        return clauses.joinToString(",", "Node(", ")")
    }
}