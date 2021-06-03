package top.branch

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
        val numSolvers = 5
        val solution = BranchAndBoundSolver(numSolvers).solve(::solveModel, ::branch)
        assertNotNull(solution)
        assertEquals(26.0, solution.objective, Util.EPS)
        assertTrue(solution.maxParallelSolves > 1)
        assertNotEquals(1, solution.maxParallelSolves)
    }

    @Test
    fun `model should be solved correctly in sequential mode`() {
        val numSolvers = 1
        val solution = BranchAndBoundSolver(numSolvers).solve(::solveModel, ::branch)
        assertNotNull(solution)
        assertEquals(26.0, solution.objective, Util.EPS)
        assertEquals(1, solution.maxParallelSolves)
    }
}

private fun solveModel(unsolvedNode: Node, cplex: IloCplex): Node {
    cplex.clearModel()
    val x = List(4) {
        cplex.numVar(0.0, 1.0, IloNumVarType.Float, "x$it")
    }
    val objExpr = cplex.linearNumExpr()
    objExpr.addTerm(24.0, x[0])
    objExpr.addTerm(2.0, x[1])
    objExpr.addTerm(20.0, x[2])
    objExpr.addTerm(4.0, x[3])
    cplex.addMaximize(objExpr)

    val constraintExpr = cplex.linearNumExpr()
    constraintExpr.addTerm(8.0, x[0])
    constraintExpr.addTerm(1.0, x[1])
    constraintExpr.addTerm(5.0, x[2])
    constraintExpr.addTerm(4.0, x[3])
    cplex.addLe(constraintExpr, 9.0, "knapsack")

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

/**
 * Branch on first fractional variable.
 */
private fun branch(solvedNode: Node): List<Node> {
    val eps = 1e-5
    val unsolvedNodes = mutableListOf<Node>()
    for ((index, value) in solvedNode.lpSolution) {
        if (value >= 1 - eps)
            continue

        val restrictions = solvedNode.restrictions
        for (n in listOf(0, 1))
            unsolvedNodes.add(
                Node(
                    restrictions = restrictions.plus(Pair(index, n)),
                    parentLpObjective = solvedNode.lpObjective
                )
            )
        break
    }
    return unsolvedNodes
}
