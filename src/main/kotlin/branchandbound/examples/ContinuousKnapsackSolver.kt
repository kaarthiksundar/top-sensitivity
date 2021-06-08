package branchandbound.examples

import branchandbound.api.INode
import branchandbound.api.ISolver
import kotlin.math.min

/**
 * Implementation of greedy algorithm to solve continuous knapsack problems to optimality. Given
 * [profits] (item utilities) and [weights] (item weights) and the knapsack [capacity], the
 * algorithm will find the combination of items with maximum value to place in the knapsack without
 * exceeding its capacity. It is assumed that the proportion of each selected item can vary
 * continuously in [0,1].
 */
class ContinuousKnapsackSolver(
    private val profits: List<Double>,
    private val weights: List<Double>,
    private val capacity: Double
) : ISolver {
    /**
     * Class that stores intermediate knapsack states when placing items in the knapsack.
     */
    private data class KnapsackState(
        val capacity: Double,
        val profit: Double,
        val solution: Map<Int, Double>,
        val integral: Boolean
    ) {
        val feasible: Boolean
            get() = capacity >= 0.0
    }

    private val eps = 1e-6

    override fun solve(unsolvedNode: INode): INode {
        // Check if restrictions are feasible.
        (unsolvedNode as Node)
        val restrictions = unsolvedNode.restrictions
        val initialState = getInitialState(restrictions)
        if (!initialState.feasible)
            return unsolvedNode.copy(lpSolved = true, lpFeasible = false)

        // Update solution with greedy algorithm.
        val indices = getIndicesSortedByUtility(restrictions.keys.toSet())
        val finalState = indices.fold(initialState) { ks, i ->
            val w = weights[i]
            val proportion = min(ks.capacity, w) / w
            if (proportion <= eps) ks
            else ks.copy(
                capacity = ks.capacity - (w * proportion),
                profit = ks.profit + (profits[i] * proportion),
                solution = ks.solution.plus(Pair(i, proportion)),
                integral = ks.integral && proportion >= 1 - eps
            )
        }
        return unsolvedNode.copy(
            lpSolved = true,
            lpFeasible = true,
            lpIntegral = finalState.integral,
            lpObjective = finalState.profit,
            lpSolution = finalState.solution
        )
    }

    private fun getInitialState(restrictions: Map<Int, Int>): KnapsackState =
        restrictions.entries.fold(
            KnapsackState(
                capacity,
                0.0,
                restrictions.filterValues { it > 0 }.mapValues { it.value.toDouble() },
                true
            )
        ) { ks, entry ->
            if (entry.value == 0) ks
            else ks.copy(
                capacity = ks.capacity - weights[entry.key],
                profit = ks.profit + profits[entry.key]
            )
        }

    private fun getIndicesSortedByUtility(fixed: Set<Int>) =
        profits.indices.asSequence().filter { it !in fixed }.map { i ->
            Pair(i, profits[i] / weights[i])
        }.sortedByDescending { it.second }.asSequence().map { it.first }.toList()
}
