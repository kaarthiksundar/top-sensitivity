package top.branch

import java.time.ZonedDateTime

data class Node(
    /**
     * Keys are variable ids. Values are integers to which variables are locked.
     */
    val restrictions: Map<Int, Int>,
    val solved: Boolean = false,
    val lpFeasible: Boolean = false,
    val lpIntegral: Boolean = false,
    val lpObjective: Double = Double.MAX_VALUE,
    val lpSolution: Map<Int, Double> = mapOf()
) : Comparable<Node> {
    private val createdAt = ZonedDateTime.now()

    override fun compareTo(other: Node): Int = when {
        lpObjective <= other.lpObjective - 1e-6 -> -1
        lpObjective >= other.lpObjective + 1e-6 -> 1
        else -> createdAt.compareTo(other.createdAt)
    }
}