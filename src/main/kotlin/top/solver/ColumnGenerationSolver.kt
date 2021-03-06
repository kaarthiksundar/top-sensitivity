package top.solver

import ilog.cplex.IloCplex
import top.data.Instance
import top.data.Parameters
import top.data.Route


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
    private val parameters: Parameters
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
    private var vertexReducedCosts = MutableList(instance.numVertices) { 0.0 }

    /**
     * 2D List of duals for enforced edges. 0.0 by default.
     */
    private var edgeDuals = List(instance.numVertices) { MutableList(instance.numVertices) {0.0} }

    /**
     * Value of the objective of the linear relaxation of the set cover model
     */
    var lpObjective = 0.0
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
        while (true) {

            println("Column Generation Iteration: $columnGenIteration")
            println("Number of Routes: ${routes.size}")

            // Solving the restricted master problem
            solveRestrictedMasterProblem()

            // Solving the pricing problem to generate columns to add to the restricted master problem
            val newRoutes = PricingProblem(
                instance,
                vehicleCoverDual,
                vertexReducedCosts,
                edgeDuals,
                parameters
            ).generateColumns()

            if (newRoutes.isEmpty()) {
                // No more columns to add. LP optimal solution has been found
                println("LP Optimal Solution Found")
                break
            } else {
                // LP optimal solution still not found. Adding routes to set cover model
                routes.addAll(newRoutes)
                columnGenIteration++
            }
        }
    }

    /**
     * Solve restricted master problem (linear relaxation of the set cover model using a subset
     * of all feasible routes).
     */
    private fun solveRestrictedMasterProblem() {
        // Creating the restricted master problem and solving
        val setCoverModel = SetCoverModel(cplex)
        setCoverModel.createModel(instance, routes)
        setCoverModel.solve()

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
        edgeDuals.forEach{it.fill(0.0)} // Resetting
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
        cplex.clearModel()
    }
}