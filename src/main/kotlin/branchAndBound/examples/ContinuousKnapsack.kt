package branchAndBound.examples

import kotlin.math.min

/**
 * Class holds an instance of the continuous Knapsack problem
 *
 * @param profit value of the each item in the knapsack
 * @param weight weight of each item that can be put in the knapsack
 * @param capacity total capacity of the knapsack
 * @param restrictions a map that mandates the amount of each item to be loaded on to the knapsack
 */
class ContinuousKnapsack(
    private val profit: List<Double>,
    private val weight: List<Double>,
    private val capacity: Double,
    private val restrictions: Map<Int, Int> = mapOf()
) {
    private var solution : MutableList<Double> = MutableList(profit.size) {
        (if (restrictions[it] != null) restrictions[it]!!.toDouble() else 0.0)
    }
    private var remainingCapacity = capacity
    private var isFeasible = true

    init {
        for ((idx, cap) in restrictions)
            remainingCapacity -= (cap * weight[idx])
        if (remainingCapacity < 0.0)
            isFeasible = false
        else
            solve()
    }

    /**
     * Solves the continuous knapsack problem using greedy algorithm
     */
    private fun solve() {
        val fixed = restrictions.keys
        val bangForBuck: MutableList<Pair<Int, Double>> = mutableListOf()
        for (idx in profit.indices) {
            if (idx in fixed) continue
            val bfb = profit[idx]/weight[idx]
            bangForBuck.add(Pair(idx, bfb))
        }
        bangForBuck.sortBy { -it.second }

        for ((idx, _) in bangForBuck) {
            val amount = min(weight[idx], remainingCapacity)
            var fraction = 1.0
            if (amount == remainingCapacity) {
                fraction = amount / weight[idx]
                remainingCapacity = 0.0
                solution[idx] = fraction
                break
            }
            remainingCapacity -= amount
            solution[idx] = fraction
        }
    }

    /**
     * @return solution list
     */
    fun getSolution() = solution

    /**
     * @return LP objective value
     */
    fun getObjectiveValue() = profit.zip(solution) { v, x -> v*x }.sum()

    /**
     * @return boolean for LP feasibility
     */
    fun isFeasible() = isFeasible
}