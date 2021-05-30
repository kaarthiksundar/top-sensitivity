package top.branch

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging

/**
 * Private static logger object accessible only within this file.
 */
private val log = KotlinLogging.logger {}

/**
 * Can only solve minimization problems. Convert max problems into min problems by multiplying the
 * objective by -1.
 */
object BranchAndBoundApi {
    fun solve(
        numSolvers: Int,
        solveNode: (Node, IloCplex) -> Node,
        branch: (Node) -> List<Node>,
    ) = runBlocking {
        // Channel that sends nodes to solver coroutines for solving.
        val preSolveChannel = Channel<Node>()
        val postSolveChannel = Channel<Node>()

        withContext(Dispatchers.Default) {
            prepareOptimizers(this, numSolvers, preSolveChannel, postSolveChannel, solveNode)
            prepareSolvedNodeProcessing(numSolvers, preSolveChannel, postSolveChannel)
            launch {
            }
        }
    }
}

/**
 * Prepare a set of coroutines to consume nodes from the unsolvedNodes channel, solve them and
 * send back the solved nodes to the solvedNodes channel. Each coroutine is created by the "launch"
 * command and remains suspended at the for loop line, i.e. waiting for the iterator of the
 * unsolvedNodes channel to release a node.
 *
 * The CPLEX objects created in this function will be created just once and be re-used for all
 * nodes released by the iterator in the loop below. This is because the scope (together with the
 * CPLEX object and the solveNode() function call will persist as long as the for loop is
 * suspended. We will go out of scope only when the unsolvedNodes channel is closed.
 */
private suspend fun prepareOptimizers(
    scope: CoroutineScope,
    numSolvers: Int,
    unsolvedChannel: Channel<Node>,
    solvedChannel: Channel<Node>,
    solveNode: (Node, IloCplex) -> Node
) {
    repeat(numSolvers) {
        scope.launch {
            val cplex = IloCplex()
            for (unsolvedNode in unsolvedChannel)
                solvedChannel.send(solveNode(unsolvedNode, cplex))
        }
    }
}

/**
 *Prepare a coroutine to consume nodes from the solvedNodes channel and use their LP/MIP objective
 * and solution values to update global bounds, prune them or branch on them. If branched, new
 * unsolved nodes will be stored in the node processor's open node queue and released to the
 * unsolvedNodes channel whenever solvers are available.
 */
private suspend fun prepareSolvedNodeProcessing(
    numSolvers: Int,
    unsolvedChannel: Channel<Node>,
    solvedChannel: Channel<Node>
) {
    for (solvedNode in solvedChannel) {
        log.info { "received $solvedNode in solvedNodes channel for nodeProcessor" }
    }
}
