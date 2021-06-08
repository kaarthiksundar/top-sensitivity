package branchandbound.examples

import branchandbound.api.INode
import branchandbound.api.ISolver


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
