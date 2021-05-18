package top.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.concert.IloRange
import ilog.cplex.IloCplex
import top.data.Instance
import top.data.Route

class SetCoverModel(private var cplex: IloCplex) {

    /**
     * List of the route variables x_k corresponding to feasible route r_k
     */
    private var routeVariable: ArrayList<IloNumVar> = arrayListOf()

    /**
     * List of the constraints in the set cover model
     */
    private var constraints: ArrayList<IloRange> = arrayListOf()

    fun createModel(instance: Instance,
                    routes: List<Route>){

        /**
         *  The (full) set cover formulation of the TOP consists of the following.
         *
         *  Variables:  Binary variable x_k corresponding to feasible route r_k
         *
         *  Parameters: a_{i, k}
         *
         *      where           a_{i, k} = 1,    if route r_k visits target v_i
         *                      a_{i, k} = 0,    otherwise
         *
         *
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
         *              sum(a_{i, k} * x_k for all routes r_k) <= 1      for all v_i \in (V - {source, destination})
         *
         *      (2)     At most m routes used
         *
         *              sum(x_k for all routes r_k) <= m
         *
         *      (3)     x_k binary
         */

        /**
         * NOTE: (FOR PERSONAL REFERENCE)
         *
         * IloNumVar:           Object representing modeling variables. It is characterized by its bounds and type.
         *                      Possible types are
         *
         *                      (1) IloNumVarType.Float
         *                      (2) IloNumVarType.Int
         *                      (3) IloNumVarType.Bool
         *
         *
         * IloLinearNumExpr:    Objects of this type represent linear expressions of the form
         *
         *                                  sum_{i = 1...n} a_i * x_i + c
         *
         *                      where x_i are variables of type IloNumVar and c and a_i are DOUBLE values
         *
         *                      A term a_i * x_i can be added to a IloLinearNumExpr object by using
         *
         *                                      addTerm(IloNumVar x_i, Double a_i)
         *
         *                      which adds the term a_i * x_i to the linear expression.
         */

        /*
            Creates an empty linear expression for scores. This will be used for the objective.
         */
        val objectiveExpression : IloLinearNumExpr = cplex.linearNumExpr()

        /*
            Creates an empty linear expression for routes. This will be used for constraint (2).
         */
        val routeExpression: IloLinearNumExpr = cplex.linearNumExpr()

        /*
            List of lists of routes.

            The kth entry corresponds to vertex v_i and the associated MutableList at
            vertexRoutes[i] is a list of indices of routes covering vertex v_i.

            That is, if a route r_k uses vertex v_i, then the index k will be in the list associated with
            vertexRoutes[i].
         */
        val vertexRoutes = List(instance.numVertices) { mutableListOf<Int>() }

        for (k in routes.indices){

            /**
             * Creating the route variable x_k and adding the corresponding x_k term for the objective and the
             * route expression for constraint (2)
             */

            routeVariable.add(cplex.numVar(0.0, 1.0, IloNumVarType.Float, "x_$k"))

            routeExpression.addTerm(1.0, routeVariable[k])
            objectiveExpression.addTerm(routes[k].score, routeVariable[k])

            /**
             * Checking which vertices are visited by route r_k and updating vertexRoutes accordingly.
             */

            routes[k].path.forEach{
                // Vertex covering not considered for the source or destination
                if (it == instance.source || it == instance.destination)
                    return@forEach
                // Indicating that vertex v_it is visited in route r_k
                // NOTE: There will never be duplicates because the feasible routes only visit nodes at most once
                vertexRoutes[it].add(k)
            }

        }

        // Finished iterating over all feasible routes being considered.

        /**
         *  SETTING THE OBJECTIVE
          */
        // Setting the sense of the objective to be a MAXIMIZATION problem.
        cplex.addMaximize(objectiveExpression)

        /**
         * SETTING CONSTRAINT
         *
         *      (1)     Each vertex (other than the source and destination) is visited at most once
         *
         *              sum(a_{i, k} * x_k for all routes r_k) <= 1      for all v_i \in (V - {source, destination})
         */


        /**
         * SETTING CONSTRAINT
         *
         *      (2)     At most m routes used
         *
         *              sum(x_k for all routes r_k) <= m
         */
        constraints.add(cplex.addLe(routeExpression, instance.numVehicles.toDouble(), "route_cover"))

        /**
         * CONSTRAINT (3) ALREADY HANDLED IN CREATION OF THE ROUTE VARIABLES
         */

    }
}