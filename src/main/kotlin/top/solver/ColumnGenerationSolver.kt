package top.solver

import ilog.cplex.IloCplex
import top.data.Instance
import top.main.SetGraph


/**
 * Class for handling the restricted master problem (RMP) using a given set cover formulation of a restricted set
 * of admissible routes. The RMP object will solve the master problem (MP) using column generation.
 *
 * @param instance [Instance] object containing relevant problem information
 * @param cplex [IloCplex] object containing the CPLEX model of the set cover model
 */
class ColumnGenerationSolver(
    instance: Instance,
    private val cplex: IloCplex
) {

    private val graph: SetGraph = instance.graph


}