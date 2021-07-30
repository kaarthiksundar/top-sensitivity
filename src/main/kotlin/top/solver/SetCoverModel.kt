package top.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.concert.IloRange
import ilog.cplex.IloCplex
import top.data.Instance
import top.data.Route
import top.main.TOPException

/**
 * Class to formulate and solve set cover model for a specified list of routes using the given
 * [cplex] instance.
 *
 *  The (full) set cover formulation of the TOP consists of the following.
 *
 *  Variables:  Binary variable x_k corresponding to feasible route r_k
 *
 *  Parameters: a_{i, k}
 *
 *      where           a_{i, k} = 1,    if route r_k visits target v_i
 *                      a_{i, k} = 0,    otherwise
 *              m       Number of vehicles in the TOP
 *
 *  Objective:  Summation of p_k * x_k for all routes r_k
 *
 *      where p_k is the prize collected along route r_k corresponding to binary variable x_k
 *
 *  Constraints:
 *
 *      (1)     Each vertex (other than the source and destination) is visited at most once
 *
 *              sum(a_{i, k} * x_k for all routes r_k) <= 1 for v_i \in (V - {source, destination})
 *
 *      (2)     At most m routes used
 *
 *              sum(x_k for all routes r_k) <= m
 *
 *      (3)     x_k binary or >= 0 depending on [asMIP]
 */
class SetCoverModel(private var cplex: IloCplex) {
    /**
     * CPLEX objective value
     */
    var objective = 0.0
        private set
    /**
     * route constraint id
     */
    private var routeConstraintId: Int = 0
    /**
     * list of constraint ids for vertex cover constraints
     */
    private lateinit var vertexCoverConstraintId: MutableList<Int?>
    /**
     * map of must-visit vertex to constraint id
     */
    private var mustVisitVertexConstraintId: MutableMap<Int, Int> = mutableMapOf()
    /**
     * map of must-visit edge to constraint id
     */
    private var mustVisitEdgeConstraintId: MutableMap<Pair<Int, Int>, Int> = mutableMapOf()
    /**
     * list of route variables
     */
    private var routeVariable: ArrayList<IloNumVar> = arrayListOf()
    /**
     * auxiliary variable to detect infeasibility
     */
    private lateinit var auxiliaryVariable: IloNumVar
    /**
     * list of all constraints
     */
    private var constraints: ArrayList<IloRange> = arrayListOf()
    /**
     * Flag to indicate whether the model has been solved.
     */
    private var solved: Boolean = false

    /**
     * Create a set cover model for the team orienteering problem.
     *
     * @param instance object with all routing problem data
     * @param routes routes to use as decision variables in set cover model
     * @param asMIP model is solved as MIP if this flag is true, and LP otherwise
     * @param mustVisitVertices Array of vertices enforced to be visited
     * @param mustVisitEdges List of edges enforced to be used
     */
    fun createModel(
        instance: Instance,
        routes: List<Route>,
        asMIP: Boolean = false,
        mustVisitVertices: IntArray = intArrayOf(),
        mustVisitEdges: List<Pair<Int, Int>> = mutableListOf()
    ) {
        /**
         *   Creates an empty linear expression for scores. This will be used for the objective.
         */
        val objectiveExpression: IloLinearNumExpr = cplex.linearNumExpr()

        /**
         *   Creates an empty linear expression for routes. This will be used for constraint (2).
         */
        val routeExpression: IloLinearNumExpr = cplex.linearNumExpr()

        /**
         * Auxiliary variable used to detect if a problem is infeasible
         */
        auxiliaryVariable = cplex.numVar(0.0, Double.MAX_VALUE, IloNumVarType.Float)

        /**
         *   List of routes.
         *
         *   The i-th entry corresponds to vertex v_i and the associated MutableList at
         *   vertexRoutes(i) is a list of indices of routes covering vertex v_i.
         *
         *   That is, if a route r_k uses vertex v_i, then the index k will be in the list
         *   vertexRoutes(i).
         */
        val vertexRoutes = List(instance.numVertices) { mutableListOf<Int>() }
        vertexCoverConstraintId = MutableList(instance.numVertices) { null }

        for (k in routes.indices) {
            /**
             * Creating the route variable x_k and adding the corresponding x_k term for the
             * objective and the route expression for constraint (2)
             *
             * The type of the route variable is also defined here. This part handles constraint
             * (3) as a result.
             */
            if (asMIP)
                routeVariable.add(cplex.numVar(0.0, 1.0, IloNumVarType.Bool, "x_$k"))
            else
                routeVariable.add(cplex.numVar(0.0, 1.0, IloNumVarType.Float, "x_$k"))

            routeExpression.addTerm(1.0, routeVariable[k])
            objectiveExpression.addTerm(routes[k].score, routeVariable[k])

            /**
             * Checking which vertices are visited by route r_k and updating vertexRoutes
             * accordingly.
             */
            routes[k].path.forEach {
                // Vertex covering not considered for the source or destination
                if (it == instance.source || it == instance.destination)
                    return@forEach
                /**
                 * Indicating that vertex v_it is visited in route r_k.
                 *
                 * By construction, index k corresponding to route r_k will never repeat in
                 * vertexRoutes(i) for v_i. This is a direct result of the fact the feasible routes
                 * visit vertices at most once.
                 */
                vertexRoutes[it].add(k)
            }
        }

        /**
         *  SETTING THE OBJECTIVE
         */
        objectiveExpression.addTerm(-100000.0, auxiliaryVariable)
        cplex.addMaximize(objectiveExpression)

        /**
         * SETTING CONSTRAINT
         *
         *      (1)     Each vertex (other than the source and destination) is visited at most once
         *
         *              sum(a_{i, k} * x_k for all routes r_k) <= 1      for all v_i \in (V - {source, destination})
         */
        for (i in 0 until instance.numVertices) {
            /**
             * Don't need to consider the source or destination vertices.
             *
             * It may be possible a vertex v_i is not visited by any of the feasible routes. This
             * can happen because of the budget requirement.
             */
            if (vertexRoutes[i].isEmpty())
                continue

            /**
             * Vertex i corresponds to a single row. Iteratively adding the terms for this row
             * before moving to the next row.
             */
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            vertexRoutes[i].forEach {
                expr.addTerm(1.0, routeVariable[it])
            }
            vertexCoverConstraintId[i] = constraints.size
            constraints.add(cplex.addLe(expr, 1.0, "vertex_cover_$i"))

        }

        /**
         * SETTING CONSTRAINT
         *
         *      (2)     At most m routes used
         *
         *              sum(x_k for all routes r_k) <= m
         */
        routeConstraintId = constraints.size
        constraints.add(
            cplex.addLe(
                routeExpression,
                instance.numVehicles.toDouble(),
                "route_cover"
            )
        )
        /**
         * CONSTRAINT (3) ALREADY HANDLED IN CREATION OF THE ROUTE VARIABLES.
         */

        /**
         * Additional constraints for must visit vertices.
         */
        mustVisitVertices.forEach {
            val expr : IloLinearNumExpr = cplex.linearNumExpr()
            vertexRoutes[it].forEach {
                it1 -> expr.addTerm(1.0, routeVariable[it1])
            }
            expr.addTerm(1.0, auxiliaryVariable)
            mustVisitVertexConstraintId[it] = constraints.size
            constraints.add(cplex.addGe(expr, 1.0, "must_visit_target_$it"))
        }

        /**
         * Additional constraints for must use edges.
         */

        // Finding which routes in the set cover model use each edge
        val edgeRoutes = mustVisitEdges.map { it to mutableListOf<Int>()}.toMap()
        mustVisitEdges.forEach {
            for (i in routes.indices) {
                if (it in routes[i].path.zipWithNext())
                    edgeRoutes.getValue(it).add(i)
            }
        }

        // Making the constraints
        mustVisitEdges.forEach{
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            edgeRoutes.getValue(it).forEach{
                it1 -> expr.addTerm(1.0, routeVariable[it1])
            }
            expr.addTerm(1.0, auxiliaryVariable)
            mustVisitEdgeConstraintId[it] = constraints.size
            constraints.add(cplex.addGe(expr, 1.0, "must_visit_edge_${it.first}_${it.second}"))
        }

    }

    /**
     * Solve the model created using the [createModel] function.
     */
    fun solve() {
        cplex.setOut(null)
        if (!cplex.solve()) {
            throw TOPException("Set covering problem infeasible")
        }
        solved = true
        objective = cplex.objValue
    }

    /**
     * Return nonzero values of route variables in solution.
     */
    fun getSolution(): List<Double> {
        if (!solved) throw TOPException("solution unavailable as model has not been solved")
        return (0 until routeVariable.size).map {
            cplex.getValue(routeVariable[it])
        }
    }

    /**
     * Function that returns the value of the auxiliary variable used to determine if the LP is infeasible.
     */
    fun getAuxiliaryVariableSolution() : Double {
        return cplex.getValue(auxiliaryVariable)
    }

    /**
     * Function that returns the dual of the fleet size constraint
     */
    fun getRouteDual(): Double = cplex.getDual(constraints[routeConstraintId])

    /**
     * Function that returns a list of the duals associated with each route decision variable.
     */
    fun getRouteVariableDuals() : List<Double> = (0 until routeVariable.size).map {
        cplex.getReducedCost(routeVariable[it])
    }

    /**
     * Function that returns the duals for the vertex covering constraints
     */
    fun getVertexDuals(): List<Double> = (0 until vertexCoverConstraintId.size).map {
        if (vertexCoverConstraintId[it] != null)
            cplex.getDual(constraints[vertexCoverConstraintId[it]!!]) else 0.0
    }

    /**
     * Function that returns the duals for the enforced vertices
     */
    fun getMustVisitVertexDuals() : Map<Int, Double> = mustVisitVertexConstraintId.map {
        it.key to cplex.getDual(constraints[it.value])
    }.toMap()

    /**
     * Function that returns the duals for the enforced edges
     */
    fun getMustVisitEdgeDuals() : Map<Pair<Int, Int>, Double> =
        mustVisitEdgeConstraintId.map{
            it.key to cplex.getDual(constraints[it.value])
        }.toMap()
}