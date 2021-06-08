package branchAndBound

import top.Util
import kotlin.test.*
import branchAndBound.examples.Node
import branchAndBound.examples.Solver
import branchAndBound.examples.branch

class BranchAndBoundSolverTest {

    @Test
    fun `first knapsack model for serial and parallel B&B solves`() {
        val profit = listOf(24.0, 2.0, 20.0, 4.0)
        val weight = listOf(8.0, 1.0, 5.0, 4.0)
        val capacity = 9.0
        for (numSolvers in listOf(1, 5)) {
            val idGenerator = generateSequence(0L) { it + 1 }.iterator()
            val solvers = List(numSolvers) { Solver(profit, weight, capacity) }
            val rootNode = Node(id = idGenerator.next())

            val solution = BranchAndBoundSolver(solvers) {
                branch((it as Node), idGenerator)
            }.solve(rootNode)
            assertNotNull(solution)
            assertTrue(solution.numCreatedNodes > 1)
            assertTrue(solution.numFeasibleNodes <= solution.numCreatedNodes)

            val expectedObjective = 26.0
            assertEquals(expectedObjective, solution.objective, Util.EPS)

            if (numSolvers > 1)
                assertTrue(solution.maxParallelSolves > 1)
            else
                assertEquals(1, solution.maxParallelSolves)
        }
    }

    @Test
    fun `second knapsack model for serial and parallel B&B solves`() {
        val profit = listOf(16.0, 22.0, 12.0, 8.0, 11.0, 19.0)
        val weight = listOf(5.0, 7.0, 4.0, 3.0, 4.0, 6.0)
        val capacity = 14.0
        for (numSolvers in listOf(1, 5)) {
            val idGenerator = generateSequence(0L) { it + 1 }.iterator()
            val solvers = List(numSolvers) { Solver(profit, weight, capacity) }
            val rootNode = Node(id = idGenerator.next())
            val solution = BranchAndBoundSolver(solvers) {
                branch((it as Node), idGenerator)
            }.solve(rootNode)
            assertNotNull(solution)
            assertTrue(solution.numCreatedNodes > 1)
            assertTrue(solution.numFeasibleNodes <= solution.numCreatedNodes)

            val expectedObjective = 43.0
            assertEquals(expectedObjective, solution.objective, Util.EPS)

            if (numSolvers > 1)
                assertTrue(solution.maxParallelSolves > 1)
            else
                assertEquals(1, solution.maxParallelSolves)
        }
    }
}