package top.data

/**
 * Data class used to hold all the parameters needed for the instance/solver.
 *
 * @param instanceName Name of instance file with .txt extension
 * @param instancePath Local path to the folder containing the instance file
 * @param timeLimitInSeconds Maximum amount of time the solver can run for
 * @param algorithm Integer indicating whether full enumeration or branch-and-price is used.
 * @param eps Constant value used as the tolerance for comparing double values (Default: 1e-5)
 * @param maxColumnsAdded Maximum number of columns added when solving the pricing problem
 * @param numInitialRoutes Number of routes used for the first iteration of the column generation scheme
 * @param numBits Constant value representing the number of bits of the operating system (Default: 64)
 */

data class Parameters(
    val instanceName: String,
    val instancePath: String,
    val outputPath: String,
    val timeLimitInSeconds: Int,
    val algorithm: Int,
    val eps: Double = 1e-5,
    val maxColumnsAdded: Int = 500,
    val numInitialRoutes: Int = 10,
    val numBits: Int = 64,
    val forwardOnly: Boolean = true,
    val backwardOnly: Boolean = false,
    val useDomination: Boolean = true
)

