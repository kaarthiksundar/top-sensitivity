package top.solver

import top.main.TOPException

/**
 * Function that branches on a solved node if the solution is not integral.
 *
 * When the solution is fractional, we first search for a vertex v_i that has
 * been visited a fractional number of times. Two branches are then derived from v_i.
 *
 * When branching on a fractional v_i, we have two children:
 *
 * (V1) Visiting of v_i is enforced
 * (V2) Visiting of v_i is forbidden
 *
 * If the solution is fractional, but the flow traversing each vertex is integer, we
 * branch on an arc (v_i, v_j) with a fractional flow. Branching on a fractional arc
 * is ONLY done when all vertices are visited an integer amount.
 *
 * When branching on a fractional arc (v_i, v_j), we have two cases:
 *
 * (A1) If v_i OR v_j is constrained to be visited, we derive two branches.
 *
 *      - First branch we make (v_i, v_j) forbidden
 *      - Second branch we make (v_i, v_j) enforced
 *
 * (A2) If NEITHER v_i or v_j are constrained to be visited, we derive three branches.
 *
 *      - First branch forbids v_i
 *      - Second branch enforces v_i and enforces (v_i, v_j)
 *      - Third branch enforces v_i and forbids (v_i, v_j)
 *
 * The master problem is automatically updated when branching.
 */
fun TOPBranch(solvedNode: TOPNode, idGenerator : Iterator<Long>, numVertices : Int) : List<TOPNode> {

    // Tolerance used for comparing doubles
    val eps = 1E-6

    // Checking the vertex reduced costs were updated
    if (solvedNode.vertexReducedCosts == null)
        throw TOPException("Vertex reduced costs null when trying to branch")

    // Finding the flow into each vertex and flow across each arc
    val vertexInflow = MutableList(numVertices) { 0.0 }
    val arcFlow : MutableMap<Pair<Int, Int>, Double> = mutableMapOf()
    for (sol in solvedNode.lpSolution) {
        for (arc in sol.first.path.zipWithNext()) {

            // Updating flow into vertex
            vertexInflow[arc.second] = vertexInflow[arc.second] + sol.second

            // Updating flow across arc
            if (arcFlow.containsKey(arc))
                arcFlow[arc] = arcFlow[arc]!! + sol.second
            else
                arcFlow[arc] = sol.second
        }
    }

    // Finding if any vertices have fractional flow. If multiple vertices available, choose the fractional flow vertex
    // with least vertex reduced cost
    var vertexToBranch : Int? = null
    var leastReducedCost = Double.MAX_VALUE

    for (vertex in 0 until numVertices) {
        // Checking if the flow is fractional
        if (vertexInflow[vertex] >= eps && 1 - vertexInflow[vertex] >= eps) {
            // Flow into vertex is integer valued

            if (solvedNode.vertexReducedCosts[vertex] <= leastReducedCost - eps) {
                // New best vertex to branch on found
                vertexToBranch = vertex
                leastReducedCost = solvedNode.vertexReducedCosts[vertex]
            }
        }
    }

    // If vertexToBranch is not null, branch on this vertex
    if (vertexToBranch != null) {

        // Branching on fractional flow vertex

        val forbiddenVisitNode = TOPNode(
            id = idGenerator.next(),
            parentLpObjective = solvedNode.lpObjective,
            mustVisitVertices = solvedNode.mustVisitVertices,
            mustVisitEdges = solvedNode.mustVisitEdges,
            forbiddenVertices = solvedNode.forbiddenVertices + vertexToBranch,
            forbiddenEdges = solvedNode.forbiddenEdges
        )

        val enforcedVisitNode = TOPNode(
            id = idGenerator.next(),
            parentLpObjective = solvedNode.lpObjective,
            mustVisitVertices = solvedNode.mustVisitVertices + vertexToBranch,
            mustVisitEdges = solvedNode.mustVisitEdges,
            forbiddenVertices = solvedNode.forbiddenVertices,
            forbiddenEdges = solvedNode.forbiddenEdges
        )

        return listOf(forbiddenVisitNode, enforcedVisitNode)
    }
    else {

        // All vertices have integer flow, so branch on a fractional arc. Among the arcs with fractional flow,
        // we will choose the one with a starting vertex that has the least reduced cost.

        // bestVertexReducedCost doesn't need to be reset since this case only happens if it was never updated.

        var arcToBranch : Pair<Int, Int>? = null

        for ((arc, flow) in arcFlow) {

            // Checking if the flow is fractional
            if (flow >= eps && 1 - flow >= eps) {

                // Flow across arc is fractional

                if (solvedNode.vertexReducedCosts[arc.first] <= leastReducedCost - eps) {
                    // New best arc to branch on found
                    arcToBranch = arc
                    leastReducedCost = solvedNode.vertexReducedCosts[arc.first]
                }

            }
        }

        if (arcToBranch == null)
            throw TOPException("Trying to branch on null arc")

        // Branching on the fractional flow arc

        /**
         * Case I: Branching on arc (v_i, v_j) when either v_i OR v_j are enforced vertices
         */
        if (arcToBranch.first in solvedNode.mustVisitVertices || arcToBranch.second in solvedNode.mustVisitVertices) {

            // Node with arc (v_i, v_j) enforced
            val enforcedArcNode = TOPNode(
                id = idGenerator.next(),
                parentLpObjective = solvedNode.lpObjective,
                mustVisitVertices = solvedNode.mustVisitVertices,
                mustVisitEdges = solvedNode.mustVisitEdges + arcToBranch,
                forbiddenVertices = solvedNode.forbiddenVertices,
                forbiddenEdges = solvedNode.forbiddenEdges
            )

            // Node with arc (v_i, v_j) forbidden
            val forbiddenArcNode = TOPNode(
                id = idGenerator.next(),
                parentLpObjective = solvedNode.lpObjective,
                mustVisitVertices = solvedNode.mustVisitVertices,
                mustVisitEdges = solvedNode.mustVisitEdges + arcToBranch,
                forbiddenVertices = solvedNode.forbiddenVertices,
                forbiddenEdges = solvedNode.forbiddenEdges
            )

            return listOf(enforcedArcNode, forbiddenArcNode)
        }
        /**
         * Case II: Neither v_i or v_j enforced
         */
        else {

            // v_i is forbidden
            val forbiddenSourceNode = TOPNode(
                id = idGenerator.next(),
                parentLpObjective = solvedNode.parentLpObjective,
                mustVisitVertices = solvedNode.mustVisitVertices,
                mustVisitEdges = solvedNode.mustVisitEdges,
                forbiddenVertices = solvedNode.forbiddenVertices + arcToBranch.first,
                forbiddenEdges = solvedNode.forbiddenEdges
            )

            // Enforce v_i and enforce (v_i, v_j)
            val enforceSourceAndEnforceArcNode = TOPNode(
                id = idGenerator.next(),
                parentLpObjective = solvedNode.parentLpObjective,
                mustVisitVertices = solvedNode.mustVisitVertices + arcToBranch.first,
                mustVisitEdges = solvedNode.mustVisitEdges + arcToBranch,
                forbiddenVertices = solvedNode.forbiddenVertices,
                forbiddenEdges = solvedNode.forbiddenEdges
            )

            // Enforce v_i and forbid (v_i, v_j)
            val enforceSourceAndForbidArcNode = TOPNode(
                id = idGenerator.next(),
                parentLpObjective = solvedNode.parentLpObjective,
                mustVisitVertices = solvedNode.mustVisitVertices + arcToBranch.first,
                mustVisitEdges = solvedNode.mustVisitEdges,
                forbiddenVertices = solvedNode.forbiddenVertices,
                forbiddenEdges = solvedNode.forbiddenEdges + arcToBranch
            )

            return listOf(forbiddenSourceNode, enforceSourceAndEnforceArcNode, enforceSourceAndForbidArcNode)
        }
    }
}