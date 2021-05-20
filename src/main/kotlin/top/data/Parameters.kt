package top.data

/**
 * Data class used to hold all the parameters needed for the instance/solver.
 *
 * @param instanceName String: Name of instance file with .txt extension
 * @param instancePath String: Local path to the folder containing the instance file
 * @param timeLimitInSeconds Int: Maximum amount of time the solver can run for
 * @param algorithm Int: Integer indicating whether full enumeration or branch-and-price is used.
 * @param eps Constant value used as the tolerance for comparing double values (Default: 1e-5)
 * @param numBits Constant value representing the number of bits of the operating system (Default: 64)
 */

data class Parameters(
    val instanceName: String,
    val instancePath: String,
    val outputPath: String,
    val timeLimitInSeconds: Int,
    val algorithm: Int,
    val eps: Double = 1e-5,
    val numBits: Int = 64
)

