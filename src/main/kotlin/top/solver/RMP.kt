package top.solver

import ilog.cplex.IloCplex
import top.data.Instance

class RMP(
    private val instance: Instance,
    private val cplex: IloCplex
) {
}