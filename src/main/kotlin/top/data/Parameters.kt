package top.data

/**
 * Data class used to hold all the parameters needed for the instance/solver.
 *
 * @property instanceName String: Name of instance file with .txt extension
 * @property instancePath String: Local path to the folder containing the instance file
 * @property timeLimitInSeconds Int: Maximum amount of time the solver can run for
 * @property eps Constant value used as the tolerance for comparing double values
 * @property numBits Constant value representing the number of bits of the operating system
 */

data class Parameters(
    val instanceName: String,
    val instancePath: String,
    val outputPath: String,
    val timeLimitInSeconds: Int,
    val eps: Double = 1e-5,
    val numBits: Int = 64,
)

