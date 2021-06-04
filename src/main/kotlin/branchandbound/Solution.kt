package branchandbound

data class Solution(
    val objective: Double,
    val numCreatedNodes: Int,
    val numFeasibleNodes: Int,
    val maxParallelSolves: Int
)