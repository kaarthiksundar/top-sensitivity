package branchandbound

import kotlinx.coroutines.channels.SendChannel
import mu.KotlinLogging
import top.Util
import java.util.*
import kotlin.math.max

/**
 * Private static logger object accessible only within this file.
 */
private val log = KotlinLogging.logger {}

class NodeProcessor(private val numSolvers: Int) {
    /**
     * Best integer solution.
     */
    private var incumbent: INode? = null

    /**
     * Nodes created by branching and yet to be solved.
     */
    private val unsolvedNodes = PriorityQueue<INode>()

    /**
     * The number of unsolved nodes sent for solving. It is initialized with 1 as the root node
     * is created directly outside of the node processor and not by branching. This way, when we
     * receive the root node, we will correctly decrease this count to 0.
     */
    private var numSolving = 1

    /**
     * Number of branch-and-bound nodes created, initialized with 1 to count the root node created
     * outside of this coroutine.
     */
    private var numCreated = 1

    /**
     * Number of branch-and-bound nodes solved.
     */
    private var numFeasible = 0

    /**
     * Maximum number of branch-and-bound nodes solved in parallel.
     */
    private var maxParallelSolves = 0

    suspend fun processNode(
        solvedNode: INode,
        unsolvedChannel: SendChannel<INode>,
        solutionChannel: SendChannel<Solution?>,
        branch: (INode) -> List<INode>
    ) {
        log.info { "processing $solvedNode" }
        --numSolving

        if (solvedNode.lpFeasible &&
            (incumbent == null || incumbent!!.lpObjective <= solvedNode.lpObjective - Util.EPS)
        ) {
            ++numFeasible
            if (solvedNode.lpIntegral)
                incumbent = solvedNode
            else {
                val children = branch(solvedNode)
                numCreated += children.size
                unsolvedNodes.addAll(children)
            }
        }

        while (unsolvedNodes.isNotEmpty() && numSolving < numSolvers) {
            unsolvedChannel.send(unsolvedNodes.remove())
            ++numSolving
        }

        maxParallelSolves = max(maxParallelSolves, numSolving)

        if (unsolvedNodes.isEmpty() && numSolving == 0)
            sendSolution(solutionChannel)
    }

    private suspend fun sendSolution(solutionChannel: SendChannel<Solution?>) {
        log.info { "number of nodes created: $numCreated" }
        log.info { "number of feasible nodes: $numFeasible" }
        log.info { "maximum parallel solves: $maxParallelSolves" }
        log.info { "sending solution to solution channel..." }
        solutionChannel.send(
            incumbent?.let {
                Solution(
                    objective = it.lpObjective,
                    numCreatedNodes = numCreated,
                    numFeasibleNodes = numFeasible,
                    maxParallelSolves = maxParallelSolves
                )
            }
        )
        log.info { "sent to solution channel" }
    }
}