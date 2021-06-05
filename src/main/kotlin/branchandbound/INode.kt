package branchandbound

interface INode : Comparable<INode> {
    val id: Long
    val parentLpObjective: Double
    val lpFeasible: Boolean
    val lpIntegral: Boolean
    val lpObjective: Double

    /**
     * If an implementation has a local search method that allows finding MIP solutions it can
     * populate this value, which will then be used to update lower bounds during branch and bound.
     */
    val mipObjective: Double?
}