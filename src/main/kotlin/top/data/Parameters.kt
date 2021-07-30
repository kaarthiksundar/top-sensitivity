package top.data

/**
 * Data class used to hold all the parameters needed for the instance/solver.
 *
 * @param instanceName Name of instance file with .txt extension
 * @param instancePath Local path to the folder containing the instance file
 * @param outputPath Local path for output KPIs
 * @param timeLimitInSeconds Maximum amount of time the solver can run for
 * @param eps Constant value used as the tolerance for comparing double values (Default: 1e-5)
 * @param maxColumnsAdded Maximum number of columns added when solving the pricing problem
 * @param numBits Constant value representing the number of bits of the operating system (Default: 64)
 * @param useDomination Boolean flag to turn on/off domination when solving the pricing problem
 * @param twoWayDomination Boolean flag to turn on/off two-way domination checking when solving the pricing problem
 * @param verticesToRemove List of vertices to consider removing for post-optimality analysis
 * @param adjustedFleetSize New fleet size to consider for post-optimality analysis
 */

data class Parameters(
    val instanceName: String,
    val instancePath: String,
    val outputPath: String,
    val timeLimitInSeconds: Int,
    val eps: Double = 1e-5,
    val maxColumnsAdded: Int = 500,
    val maxPathsAfterSearch: Int = 10,
    val numBits: Int = 64,
    val useDomination: Boolean = true,
    val twoWayDomination: Boolean = true,
    val verticesToRemove : List<Int>,
    val adjustedFleetSize : Int
)

