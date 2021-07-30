package top.solver

import branchandbound.api.INode
import branchandbound.api.ISolver
import ilog.cplex.IloCplex
import top.data.Instance
import top.data.Parameters

class TOPSolver(
    private val instance: Instance,
    private val cplex: IloCplex,
    private val parameters: Parameters
) : ISolver {

    /**
     * Function that solves a given unsolved node.
     *
     * The unsolved node may have restrictions in the form of enforced vertices/edges and forbidden vertices/edges.
     * These restrictions are passed into the ColumnGenerationSolver object. Forbidden vertices/edges are removed from
     * the graph inside ColumnGenerationSolver and enforced vertices/edges are passed into ColumnGenerationSolver and
     * then further passed into SetCoverModel to add the corresponding constraints.
     *
     * The unsolved node is then solved with the restricted graph and additional constraints using column generation.
     *
     * @return [INode] object corresponding to the newly solved node
     */
    override fun solve(unsolvedNode: INode): INode {

        // Type casting
        (unsolvedNode as TOPNode)

        val cgs = ColumnGenerationSolver(
            instance = instance,
            cplex = cplex,
            parameters = parameters,
            mustVisitVertices = unsolvedNode.mustVisitVertices,
            mustVisitEdges = unsolvedNode.mustVisitEdges,
            forbiddenVertices = unsolvedNode.forbiddenVertices,
            forbiddenEdges = unsolvedNode.forbiddenEdges
        )

        // Solving the RMP associated with this node
        cgs.solve()

        // Returning a solved node object with updated properties from the solution to the RMP
        return TOPNode(
            parent = unsolvedNode.parent,
            id = unsolvedNode.id,
            parentLpObjective = unsolvedNode.parentLpObjective,
            lpFeasible = !cgs.lpInfeasible,
            lpIntegral = cgs.lpIntegral,
            lpObjective = cgs.lpObjective,
            mustVisitVertices = unsolvedNode.mustVisitVertices,
            mustVisitEdges = unsolvedNode.mustVisitEdges,
            forbiddenVertices = unsolvedNode.forbiddenVertices,
            forbiddenEdges = unsolvedNode.forbiddenEdges,
            lpSolution = cgs.lpSolution,
            mipSolution = cgs.mipSolution,
            mipObjective = cgs.mipObjective,
            vertexReducedCosts = cgs.vertexReducedCosts,
            dualLPUpperBound = cgs.dualLPUpperBound
        )
    }
}