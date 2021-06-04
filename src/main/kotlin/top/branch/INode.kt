package top.branch

interface INode : Comparable<INode> {
    val parentLpObjective: Double
    val lpFeasible: Boolean
    val lpIntegral: Boolean
    val lpObjective: Double
}