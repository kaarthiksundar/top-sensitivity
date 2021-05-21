package top.solver

import ilog.cplex.IloCplex
import top.data.Instance
import top.data.Parameters
import top.data.Route


/**
 * Class for handling the restricted master problem (RMP) using a given set cover formulation of a restricted set
 * of admissible routes. The object will solve the master problem (MP) using column generation.
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
     * List of the [Route] objects to be used in the set cover model
     */
    private var routes: MutableList<Route> = mutableListOf()

    /**
     * Dual variable associated with the constraint that there are at most m vehicles used
     */
    private var vehicleCoverDual = 0.0

    /**
     * List of reduced costs from the dual values of the linear relaxation of the set cover model
     */
    private var vertexReducedCosts = MutableList(instance.numVertices) {0.0}

    /**
     * Value of the objective of the linear relaxation of the set cover model
     */
    var lpObjective = 0.0
        private set

    /**
     * List of pairs of Route objects and the corresponding value of the route variable for the linear relaxation of
     * the set cover model
     */
    var lpSolution = mutableListOf<Pair<Route, Double>>()

    /**
     * Initially populating the list of routes using a specified number of feasible routes
     */
    init{
        // TODO: Check if there's a better way of doing this step.
        routes.addAll(initialRoutes(instance, parameters.numInitialRoutes))
    }

    /**
     * Function that uses column generation to solve the master problem (linear relaxation of the set cover model)
     */
    fun solve(){
        var columnGenIteration = 0
        while (true){

            println("Column Generation Iteration: $columnGenIteration")
            println("Number of Routes: ${routes.size}")

            // Solving the restricted master problem
            solveRestrictedMasterProblem()

            // Solving the pricing problem to generate columns to add to the restricted master problem
            val newRoutes = PricingProblem(instance, vehicleCoverDual, vertexReducedCosts, parameters).generateColumns()

            if (newRoutes.isEmpty()){
                // No more columns to add. LP optimal solution has been found
                println("LP Optimal Solution Found")
                break
            }
            else{
                // LP optimal solution still not found. Adding routes to set cover model
                routes.addAll(newRoutes)
                columnGenIteration++
            }
        }
    }

    /**
     * Function that solves the restricted master problem (linear relaxation of the set cover model using a subset
     * of all feasible routes)
     */
    private fun solveRestrictedMasterProblem(){

        // Creating the restricted master problem and solving
        val setCoverModel = SetCoverModel(cplex)
        setCoverModel.createModel(instance, routes)
        setCoverModel.solve()

        // Collecting the dual variable corresponding to the number of vehicles constraint, constraint (3).
        vehicleCoverDual = setCoverModel.getRouteDual()

        // Collecting the reduced costs of each vertex given the dual and prizes
        val vertexDuals = setCoverModel.getVertexDuals()
        for (i in 0 until instance.numVertices){
            vertexReducedCosts[i] = vertexDuals[i] - instance.scores[i]
        }

        // Updating values for LP solution. Parameter.eps used as a tolerance for whether a route is included
        lpObjective = setCoverModel.objective
        lpSolution.clear()
        val setCoverSolution = setCoverModel.getSolution()
        for (i in setCoverSolution.indices){
            if (setCoverSolution[i] >= parameters.eps){
                lpSolution.add(Pair(routes[i], setCoverSolution[i]))
            }
        }
        cplex.clearModel()
    }

}