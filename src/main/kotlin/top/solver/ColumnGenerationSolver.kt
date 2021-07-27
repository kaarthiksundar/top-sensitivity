package top.solver

import mu.KLogging
import ilog.cplex.IloCplex
import top.data.Instance
import top.data.Parameters
import top.data.Route
import top.main.SetGraph
import top.main.TOPException
import top.main.getCopy
import kotlin.math.max


/**
 * Class to handle the restricted master problem (RMP) using a given set cover formulation of a
 * restricted set of admissible routes. The object will solve the master problem (MP) using column
 * generation.
 *
 * @param instance [Instance] object containing relevant problem information
 * @param cplex [IloCplex] object containing the CPLEX model of the set cover model
 * @param parameters [Parameters] object containing relevant information for the solver
 */
class ColumnGenerationSolver(
    private val instance: Instance,
    private val cplex: IloCplex,
    private val parameters: Parameters,
    private val mustVisitVertices : IntArray = intArrayOf(),
    private val mustVisitEdges : List<Pair<Int, Int>> = listOf(),
    private val forbiddenVertices : IntArray = intArrayOf(),
    private val forbiddenEdges : List<Pair<Int, Int>> = listOf()
) {
    /**
     * Routes to use in the set cover model
     */
    private var routes: MutableList<Route> = mutableListOf()

    /**
     * Dual variable associated with the constraint that there are at most m vehicles used
     */
    private var vehicleCoverDual = 0.0

    /**
     * List of reduced costs from the dual values of the linear relaxation of the set cover model
     */
    var vertexReducedCosts = MutableList(instance.numVertices) { 0.0 }
        private set

    /**
     * 2D List of duals for enforced edges. 0.0 by default.
     */
    private var edgeDuals = List(instance.numVertices) { MutableList(instance.numVertices) {0.0} }

    /**
     * Value of the objective of the linear relaxation of the set cover model
     */
    var lpObjective = 0.0
        private set

    var lpOptimal = false
        private set

    var lpInfeasible = true
        private set

    var mipObjective = 0.0
        private set

    var mipSolution = listOf<Route>()
        private set

    var lpIntegral = false
        private set

    var dualLPUpperBound = Double.POSITIVE_INFINITY
        private set

    /**
     * List of pairs of Route objects and the corresponding value of the route variable for the
     * linear relaxation of the set cover model.
     */
    var lpSolution = mutableListOf<Pair<Route, Double>>()

    /**
     * Solve master problem (linear relaxation for set cover model) using column generation.
     */
    fun solve() {
        var columnGenIteration = 0
        val reducedGraph = generateReducedGraph()
        while (true) {

            logger.info("Column Generation Iteration: $columnGenIteration")
            logger.info("Number of Routes: ${routes.size}")

            // Solving the restricted master problem
            solveRestrictedMasterProblem()

            // Solving the pricing problem to generate columns to add to the restricted master problem
            val newRoutes = PricingProblem(
                instance,
                reducedGraph,
                vehicleCoverDual,
                vertexReducedCosts,
                edgeDuals,
                parameters
            ).generateColumns()

            if (newRoutes.isEmpty()) {
                // No more columns to add. LP optimal solution has been found
                logger.info("LP Optimal Solution Found")
                logger.info("LP Objective: $lpObjective")
                lpOptimal = true
                break
            } else {
                // LP optimal solution still not found. Adding routes to set cover model
                routes.addAll(newRoutes)
                columnGenIteration++
            }
        }
        // Finished solving the master problem associated with the current node

        // If the LP is feasible, solve the MIP in order to get a better lower bound
        if (!lpInfeasible)
            solveRestrictedMasterProblem(asMIP = true)

        // Checking if the LP's solution is integer-valued
        lpIntegral = true
        for (sol in lpSolution) {
            if (sol.second >= parameters.eps && 1 - sol.second >= parameters.eps) {
                lpIntegral = false
                break
            }
        }
    }

    /**
     * Solve restricted master problem (linear relaxation of the set cover model using a subset
     * of all feasible routes).
     */
    private fun solveRestrictedMasterProblem(asMIP : Boolean = false) {
        // Creating the restricted master problem and solving
        val setCoverModel = SetCoverModel(cplex)
        setCoverModel.createModel(
            instance = instance,
            routes = routes,
            asMIP = asMIP,
            mustVisitVertices = mustVisitVertices,
            mustVisitEdges = mustVisitEdges)
        setCoverModel.solve()

        if (asMIP) {

            // Collecting the MIP solution
            mipObjective = setCoverModel.objective
            logger.debug("MIP Objective: $mipObjective")
            val setCoverSolution = setCoverModel.getSolution()
            val selectedRoutes = mutableListOf<Route>()
            for (i in setCoverSolution.indices) {
                // Checking if the value is effectively non-zero
                if (setCoverSolution[i] >= parameters.eps)
                    selectedRoutes.add(routes[i])
            }
            mipSolution = selectedRoutes
        }
        else {
            // Collect dual variable corresponding to the number of vehicles constraint.
            vehicleCoverDual = setCoverModel.getRouteDual()

            // Collect reduced costs of each vertex given the dual and prizes.
            val vertexDuals = setCoverModel.getVertexDuals()
            for (i in 0 until instance.numVertices) {
                vertexReducedCosts[i] = vertexDuals[i] - instance.scores[i]
            }

            // Updating the reduced costs by duals for enforced vertices
            for ((vertex, dual) in setCoverModel.getMustVisitVertexDuals()) {
                vertexReducedCosts[vertex] += dual
            }

            // Storing duals of enforced edges. Used in the pricing problem
            edgeDuals.forEach { it.fill(0.0) } // Resetting
            for ((edge, dual) in setCoverModel.getMustVisitEdgeDuals())
                edgeDuals[edge.first][edge.second] = dual

            // Update LP solution values.
            lpObjective = setCoverModel.objective
            lpSolution.clear()
            val setCoverSolution = setCoverModel.getSolution()
            for (i in setCoverSolution.indices) {
                if (setCoverSolution[i] >= parameters.eps) {
                    lpSolution.add(Pair(routes[i], setCoverSolution[i]))
                }
            }

            // Checking if the LP is infeasible by examining the value of the auxiliary variable
            lpInfeasible = setCoverModel.getAuxiliaryVariableSolution() >= parameters.eps

            // Updating dual LP upper bound. When LP infeasible, duals used are from Phase I of Simplex
            updateDualUpperBound(setCoverModel)

        }
        cplex.clearModel()
    }

    private fun generateReducedGraph() : SetGraph {
        val reducedGraph = instance.graph.getCopy()

        // Removing forbidden vertices (removes incident edges as well)
        for (vertex in forbiddenVertices) {

            // Checking the vertex exists. If so, remove.
            if (reducedGraph.containsVertex(vertex))
                reducedGraph.removeVertex(vertex)
            else if (!instance.graph.containsVertex(vertex))
                throw TOPException("Attempting to remove non-existent vertex $vertex from graph")
        }

        // Removing forbidden edges
        for (edge in forbiddenEdges) {
            // Checking the edge exists. If so, remove.
            if (reducedGraph.containsEdge(edge.first, edge.second))
                reducedGraph.removeEdge(edge.first, edge.second)
            else if (!instance.graph.containsEdge(edge.first, edge.second))
                throw TOPException("Attempting to remove non-existent arc $edge from graph")

        }

        return reducedGraph
    }

    private fun updateDualUpperBound(setCoverModel: SetCoverModel) {

        dualLPUpperBound = 0.0

        // Adding dual variables for vertex cover constraints
        dualLPUpperBound += setCoverModel.getVertexDuals().sum()

        // Duals corresponding to enforced vertices
        for ((_, dual) in setCoverModel.getMustVisitVertexDuals()) {
            dualLPUpperBound -= dual
        }

        // Duals corresponding to enforced arcs
        for ((_, dual) in setCoverModel.getMustVisitEdgeDuals()) {
            dualLPUpperBound -= dual
        }

        // Dual term corresponding to fleet size constraint
        dualLPUpperBound += setCoverModel.getRouteDual() * (instance.numVehicles + 1)

        for (routeVariableDual in setCoverModel.getRouteVariableDuals()) {
            dualLPUpperBound += max(routeVariableDual, 0.0)
        }

    }

    companion object : KLogging()
}