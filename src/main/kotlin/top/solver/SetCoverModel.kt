package top.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.cplex.IloCplex
import top.data.Instance
import top.data.Route

class SetCoverModel(private var cplex: IloCplex) {

    private var routeVariable: ArrayList<IloNumVar> = arrayListOf()

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
         *              sum(a_{i, k} * x_k for all routes r_k) <= 1      v_i \in V - {source, destination}
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
            Creates an empty linear expression. This will be used for constraint (2).
         */
        val routeExpression: IloLinearNumExpr = cplex.linearNumExpr()

        for (i in 0 until routes.size){

            /*
                Adding the route variable, x_k, for feasible route r_k

                numVar( Double lb,                      Lower bound on variable
                        Double ub,                      Upper bound on variable
                        IloNumVarType type,             Type of variable (boolean, float, etc.)
                        String s)                       String name of variable

             */
            routeVariable.add(cplex.numVar(0.0, 1.0, IloNumVarType.Float, "x_$i"))
            routeExpression.addTerm(1.0, routeVariable[i])

        }

    }
}