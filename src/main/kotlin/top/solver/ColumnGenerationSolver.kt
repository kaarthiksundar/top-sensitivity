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
 */
class ColumnGenerationSolver(
    private val instance: Instance,
    private val cplex: IloCplex,
    private val parameters: Parameters,
    numInitialRoutes: Int = 4
) {

    private var routes: MutableList<Route> = mutableListOf()

    private var vehicleCoverDual = 0.0

    var lpObjective = 0.0
        private set

    var lpSolution = mutableListOf<Pair<Route, Double>>()

    var vertexReducedCosts = MutableList(instance.numVertices) {0.0}
        private set

    init{
        routes.addAll(initialRoutes(instance, numInitialRoutes))
        println("Initial Routes: $routes")
    }

    fun solve(){
        solveRestrictedMasterProblem()
    }

    private fun solveRestrictedMasterProblem(){

        // Creating the restricted master problem and solving
        println("Creating Set Cover Model")
        val setCoverModel = SetCoverModel(cplex)
        setCoverModel.createModel(instance, routes)
        println("Solving Set Cover Model")
        setCoverModel.solve()

        // Collecting the dual variable corresponding to the number of vehicles constraint, constraint (3).
        vehicleCoverDual = setCoverModel.getRouteDual()

        // Collecting the reduced costs of each vertex given the dual and prizes
        val vertexDuals = setCoverModel.getVertexDuals()
        for (i in 0 until instance.numVertices){
            vertexReducedCosts[i] = vertexDuals[i] - instance.scores[i]
        }

        // Updating values for LP solution
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