package top.branch

import top.Util.EPS
import java.time.ZonedDateTime

class Node(
    /**
     * Keys are variable ids. Values are integers to which variables are locked.
     */
    val restrictions: Map<Int, Int>,
    /**
     * LP objective of node whose branching creates the current node.
     */
    val parentLpObjective: Double,
    val lpFeasible: Boolean = false,
    val lpIntegral: Boolean = false,
    val lpObjective: Double = Double.MAX_VALUE,
    val lpSolution: Map<Int, Double> = mapOf()
) : Comparable<Node> {
    private val createdAt = ZonedDateTime.now()

    override fun compareTo(other: Node): Int = when {
        parentLpObjective <= other.parentLpObjective - EPS -> -1
        parentLpObjective >= other.parentLpObjective + EPS -> 1
        else -> createdAt.compareTo(other.createdAt)
    }

    override fun toString(): String =
        "Node(restrictions=$restrictions,obj=$lpObjective,sln=$lpSolution)"
}