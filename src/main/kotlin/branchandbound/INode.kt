package branchandbound

interface INode : Comparable<INode> {
    val id: Long
    val parentLpObjective: Double
    val lpFeasible: Boolean
    val lpIntegral: Boolean
    val lpObjective: Double
}