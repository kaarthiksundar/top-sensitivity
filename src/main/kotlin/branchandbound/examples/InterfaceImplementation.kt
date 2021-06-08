package branchandbound.examples

import branchandbound.INode
import branchandbound.ISolver

/**
 * Implements the node interface with additional fields
 */
data class Node(
    override val id: Long,
    /**
     * Keys are variable ids. Values are integers to which variables are locked.
     */
    val restrictions: Map<Int, Int> = mapOf(),
    override val parentLpObjective: Double = Double.MAX_VALUE,
    val lpSolved: Boolean = false,
    override val lpFeasible: Boolean = false,
    override val lpIntegral: Boolean = false,
    override val lpObjective: Double = Double.MAX_VALUE,
    val lpSolution: Map<Int, Double> = mapOf()
) : INode {
    override val mipObjective: Double? = null

    override fun toString(): String {
        val clauses = mutableListOf("id=$id")
        if (parentLpObjective == Double.MAX_VALUE)
            clauses.add("parentLp=$parentLpObjective")
        else
            clauses.add("parentLp=%.2f".format(parentLpObjective))
        if (!lpSolved)
            clauses.add("unsolved")
        else if (lpFeasible) {
            clauses.add("lp=%.2f".format(lpObjective))
            clauses.add(if (lpIntegral) "integral" else "fractional")
        } else clauses.add("infeasible")

        return clauses.joinToString(",", "Node(", ")")
    }
}

/**
 * Implements the solver interface for a knapsack problem
 */
class Solver(
    private val profit: List<Double>,
    private val weight: List<Double>,
    private val capacity: Double,
) : ISolver {

    override fun solve(unsolvedNode: INode): INode {
        (unsolvedNode as Node)
        val knapsack = ContinuousKnapsack(
            profit, weight, capacity, unsolvedNode.restrictions)

        val eps = 1e-6
        return if (knapsack.isFeasible()) {
            val solutionMap = mutableMapOf<Int, Double>()
            var integral = true
            for ((i, value) in knapsack.getSolution().withIndex()) {
                if (value >= eps) {
                    solutionMap[i] = value
                    if (integral && value <= 1 - eps)
                        integral = false
                }
            }
            unsolvedNode.copy(
                lpSolved = true,
                lpFeasible = true,
                lpIntegral = integral,
                lpObjective = knapsack.getObjectiveValue(),
                lpSolution = solutionMap
            )
        } else unsolvedNode.copy(lpSolved = true, lpFeasible = false)
    }
}

/**
* Branching function on first fractional variable.
*/
fun branch(solvedNode: Node, idGenerator: Iterator<Long>): List<Node> {
    val eps = 1e-6
    for ((index, value) in solvedNode.lpSolution) {
        if (value >= 1 - eps)
            continue

        val restrictions = solvedNode.restrictions
        return listOf(0, 1).map {
            Node(
                id = idGenerator.next(),
                restrictions = restrictions.plus(Pair(index, it)),
                parentLpObjective = solvedNode.lpObjective
            )
        }
    }
    return listOf()
}
