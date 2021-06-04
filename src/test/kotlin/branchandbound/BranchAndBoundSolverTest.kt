package branchandbound

import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.cplex.IloCplex
import top.Util
import kotlin.test.*

class BranchAndBoundSolverTest {
    @Test
    fun `binary problems should be solved correctly in parallel and sequential modes`() {
        for (useModel1 in listOf(true, false)) {
            for (numSolvers in listOf(1, 5)) {
                val idGenerator = generateSequence(0) { it + 1 }.iterator()
                val solvers = List(numSolvers) { CplexSolver(useModel1) }
                val rootNode = Node(id = idGenerator.next())
                val solution = BranchAndBoundSolver(solvers) {
                    branch((it as Node), idGenerator)
                }.solve(rootNode)
                assertNotNull(solution)
                assertTrue(solution.numCreatedNodes > 1)
                assertTrue(solution.numFeasibleNodes <= solution.numCreatedNodes)

                val expectedObjective = CplexSolver(useModel1).getMIPObjective()
                assertEquals(expectedObjective, solution.objective, Util.EPS)

                if (numSolvers > 1)
                    assertTrue(solution.maxParallelSolves > 1)
                else
                    assertEquals(1, solution.maxParallelSolves)
            }
        }
    }
}

private data class Node(
    private val id: Int,
    /**
     * Keys are variable ids. Values are integers to which variables are locked.
     */
    val restrictions: Map<Int, Int> = mapOf(),
    override val parentLpObjective: Double = Double.MAX_VALUE,
    override val lpFeasible: Boolean = false,
    override val lpIntegral: Boolean = false,
    override val lpObjective: Double = Double.MAX_VALUE,
    val lpSolution: Map<Int, Double> = mapOf()
) : INode {
    override fun compareTo(other: INode): Int =
        parentLpObjective.compareTo(other.parentLpObjective)

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

private class CplexSolver(private val model1: Boolean = true) : ISolver {
    private val cplex = IloCplex()

    private fun buildModel1(x: Array<IloNumVar>) {
        val objCoefficients = doubleArrayOf(24.0, 2.0, 20.0, 4.0)
        cplex.addMaximize(cplex.scalProd(objCoefficients, x))

        val constraintCoefficients = doubleArrayOf(8.0, 1.0, 5.0, 4.0)
        cplex.addLe(cplex.scalProd(constraintCoefficients, x), 9.0, "knapsack")
    }

    private fun buildModel2(x: Array<IloNumVar>) {
        val objCoefficients = doubleArrayOf(16.0, 22.0, 12.0, 8.0, 11.0, 19.0)
        cplex.addMaximize(cplex.scalProd(objCoefficients, x))

        val constraintCoefficients = doubleArrayOf(5.0, 7.0, 4.0, 3.0, 4.0, 6.0)
        cplex.addLe(cplex.scalProd(constraintCoefficients, x), 14.0, "knapsack")
    }

    fun getMIPObjective(): Double {
        cplex.clearModel()
        val x = cplex.numVarArray(if (model1) 4 else 6, 0.0, 1.0)
        if (model1)
            buildModel1(x)
        else
            buildModel2(x)
        cplex.add(cplex.conversion(x, IloNumVarType.Bool))
        cplex.setOut(null)
        cplex.solve()
        return cplex.objValue
    }

    override fun solve(unsolvedNode: INode): INode {
        (unsolvedNode as Node)
        cplex.clearModel()
        val x = cplex.numVarArray(if (model1) 4 else 6, 0.0, 1.0)
        if (model1)
            buildModel1(x)
        else
            buildModel2(x)

        for ((index, value) in unsolvedNode.restrictions) {
            cplex.addEq(x[index], value.toDouble(), "restriction_x$index")
        }

        cplex.setOut(null)
        val eps = 1e-5
        return if (cplex.solve()) {
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
            unsolvedNode.copy(
                lpFeasible = true,
                lpIntegral = integral,
                lpObjective = cplex.objValue,
                lpSolution = solutionMap
            )
        } else unsolvedNode.copy(lpFeasible = false)
    }
}

/**
 * Branch on first fractional variable.
 */
private fun branch(solvedNode: Node, idGenerator: Iterator<Int>): List<Node> {
    for ((index, value) in solvedNode.lpSolution) {
        if (value >= 1 - Util.EPS)
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
