package top.branch

import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.cplex.IloCplex
import top.Util
import kotlin.test.*

/**
 * These tests will check whether the branch and bound API solves and calculates solution stats
 * for the following model.
 *
 *     Maximize   24 x1 + 2 x2 + 20 x3 + 4 x4
 *     subject to 8 x1 + x2 + 5x3 + 4x4 <= 9
 *     x1,x2,x3,x4 binary
 *
 *     The solution for this problem is 26 with x1=1, x2=1.
 */
class BranchAndBoundSolverTest {
    @Test
    fun `model should be solved correctly in parallel mode`() {
        val solvers = List(5) { CplexSolver() }
        val solution = BranchAndBoundSolver(solvers).solve(::branch)
        assertNotNull(solution)
        assertEquals(26.0, solution.objective, Util.EPS)
        assertTrue(solution.maxParallelSolves > 1)
        assertNotEquals(1, solution.maxParallelSolves)
    }

    @Test
    fun `model should be solved correctly in sequential mode`() {
        val solvers = listOf(CplexSolver())
        val solution = BranchAndBoundSolver(solvers).solve(::branch)
        assertNotNull(solution)
        assertEquals(26.0, solution.objective, Util.EPS)
        assertEquals(1, solution.maxParallelSolves)
    }
}

private class CplexSolver : ISolver {
    private val cplex = IloCplex()

    private fun buildSimpleModel(x: Array<IloNumVar>) {
        cplex.clearModel()
        val objCoefficients = doubleArrayOf(24.0, 2.0, 20.0, 4.0)
        cplex.addMaximize(cplex.scalProd(objCoefficients, x))

        val constraintCoefficients = doubleArrayOf(8.0, 1.0, 5.0, 4.0)
        cplex.addLe(cplex.scalProd(constraintCoefficients, x), 9.0, "knapsack")
    }

    override fun solve(unsolvedNode: Node): Node {
        cplex.clearModel()
        val x = cplex.numVarArray(4, 0.0, 1.0)
        buildSimpleModel(x)

        for ((index, value) in unsolvedNode.restrictions) {
            cplex.addEq(x[index], value.toDouble(), "restriction_x$index")
        }

        cplex.setOut(null)
        val eps = 1e-5
        return if (!cplex.solve()) Node(
            restrictions = unsolvedNode.restrictions,
            parentLpObjective = unsolvedNode.parentLpObjective,
        ) else {
            val solutionMap = mutableMapOf<Int, Double>()
            var integral = true
            for ((i, xVar) in x.withIndex()) {
                val xValue = cplex.getValue(xVar)
                if (xValue >= eps) {
                    solutionMap[i] = xValue
                    if (integral && xValue <= 1 - eps)
                        integral = false
                }
            }
            Node(
                restrictions = unsolvedNode.restrictions,
                parentLpObjective = unsolvedNode.parentLpObjective,
                lpFeasible = true,
                lpIntegral = integral,
                lpObjective = cplex.objValue,
                lpSolution = solutionMap
            )
        }
    }
}

/**
 * Branch on first fractional variable.
 */
private fun branch(solvedNode: Node): List<Node> {
    for ((index, value) in solvedNode.lpSolution) {
        if (value >= 1 - Util.EPS)
            continue

        val restrictions = solvedNode.restrictions
        return listOf(0, 1).map {
            Node(
                restrictions = restrictions.plus(Pair(index, it)),
                parentLpObjective = solvedNode.lpObjective
            )
        }
    }
    return listOf()
}
