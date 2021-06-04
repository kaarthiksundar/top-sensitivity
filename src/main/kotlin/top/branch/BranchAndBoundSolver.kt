package top.branch

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging

/**
 * Private static logger object accessible only within this file.
 */
private val log = KotlinLogging.logger {}

/**
 * Generic branch-and-bound algorithm that is agnostic of linear program solving, and focuses only
 * on solving nodes in parallel and maintaining bounds, solutions and other statistics in a
 * thread-safe manner.
 *
 * NOTE: This class can only solve maximization problems. Convert min problems into max problems by
 * multiplying the objective by -1.
 */
class BranchAndBoundSolver(private val solvers: List<ISolver>) {
    /**
     * Channel into which branch-and-bound nodes to be solved as restricted LPs will be sent.
     */
    private val unsolvedChannel = Channel<Node>()

    /**
     * Channel into which solved nodes will be sent for pruning or branching.
     */
    private val solvedChannel = Channel<Node>()

    /**
     * Channel that will receive a single value upon completion of branch-and-bound. The value will
     * be a [Solution] object if a feasible integer solution is found, and null otherwise.
     */
    private val solutionChannel = Channel<Solution?>()

    /**
     * Create a multi-threaded coroutine context and run the branch-and-bound algorithm in it.
     */
    fun solve(branch: (Node) -> List<Node>): Solution? =
        runBlocking {
            withContext(Dispatchers.Default) {
                runBranchAndBound(this, branch)
            }
        }

    /**
     * Run branch-and-bound in the [scope], the given coroutine scope. Use the given [branch]
     * function to create new nodes from solved nodes with fractional solutions.
     */
    private suspend fun runBranchAndBound(
        scope: CoroutineScope, branch: (Node) -> List<Node>
    ): Solution? {
        prepareOptimizers(scope)
        prepareSolvedNodeProcessing(scope, branch)

        scope.launch {
            unsolvedChannel.send(Node(restrictions = mapOf(), parentLpObjective = Double.MAX_VALUE))
        }
        val solution = solutionChannel.receive()
        log.info { "received solution" }
        scope.coroutineContext.cancelChildren()
        return solution
    }

    /**
     * Prepare a set of coroutines to consume nodes from the unsolvedNodes channel, solve them and
     * send back the solved nodes to the solvedNodes channel. Each coroutine is created by the
     * "launch" command and remains suspended at the for loop line, i.e. waiting for the iterator
     * of the unsolvedNodes channel to release a node.
     *
     * The CPLEX objects created in this function will be created just once and be re-used for all
     * nodes released by the iterator in the loop below. This is because the scope (together with
     * the CPLEX object and the solveNode() function call will persist as long as the for loop is
     * suspended. We will go out of scope only when the unsolvedNodes channel is closed.
     */
    private suspend fun prepareOptimizers(scope: CoroutineScope) {
        for (solver in solvers) {
            scope.launch {
                for (unsolvedNode in unsolvedChannel)
                    solvedChannel.send(solver.solve(unsolvedNode))
            }
        }
    }

    /**
     * Prepare a coroutine to consume nodes from the solvedNodes channel and use their LP/MIP
     * objective and solution values to update global bounds, prune them or branch on them. If
     * branched, new unsolved nodes will be stored in the node processor's open node queue and
     * released to the unsolvedNodes channel whenever solvers are available.
     */
    private suspend fun prepareSolvedNodeProcessing(
        scope: CoroutineScope, branch: (Node) -> List<Node>
    ) = scope.launch {
        val nodeProcessor = NodeProcessor(solvers.size)
        for (solvedNode in solvedChannel)
            nodeProcessor.processNode(solvedNode, unsolvedChannel, solutionChannel, branch)
    }
}
