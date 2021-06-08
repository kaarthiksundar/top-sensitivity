package branchAndBound

import kotlin.test.*
import branchAndBound.examples.Node
import branchAndBound.examples.Solver
import branchAndBound.examples.branch

class BranchAndBoundSolverTest {

    private val eps = 1e-5
    @Test
    fun `tests for serial and parallel B&B solves`() {
        val problemIds = listOf(1, 2)
        for (id in problemIds) {
            val problem = getProblem(id)
            for (numSolvers in listOf(1, 5)) {
                val idGenerator = generateSequence(0L) { it + 1 }.iterator()
                val solvers = List(numSolvers) {
                    Solver(problem!!.profit, problem.weight, problem.capacity) }
                val rootNode = Node(id = idGenerator.next())

                val solution = BranchAndBoundSolver(solvers) {
                    branch((it as Node), idGenerator)
                }.solve(rootNode)
                assertNotNull(solution)
                assertTrue(solution.numCreatedNodes > 1)
                assertTrue(solution.numFeasibleNodes <= solution.numCreatedNodes)

                assertEquals(problem!!.objective, solution.objective, eps)

                if (numSolvers > 1)
                    assertTrue(solution.maxParallelSolves > 1)
                else
                    assertEquals(1, solution.maxParallelSolves)
            }
        }
    }
}

private data class KnapsackInstance(
    val profit: List<Double>,
    val weight: List<Double>,
    val capacity: Double,
    val objective: Double
)

private fun getProblem(problemId: Int): KnapsackInstance? {
    when (problemId) {
        1 -> return KnapsackInstance(
            profit = listOf(24.0, 2.0, 20.0, 4.0),
            weight = listOf(8.0, 1.0, 5.0, 4.0),
            capacity = 9.0, objective = 26.0
        )
        2 -> return KnapsackInstance(
            profit = listOf(16.0, 22.0, 12.0, 8.0, 11.0, 19.0),
            weight = listOf(5.0, 7.0, 4.0, 3.0, 4.0, 6.0),
            capacity = 14.0, objective = 43.0
        )
    }
    return null
}