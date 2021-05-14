package top.data

/**
 * Singleton used to hold all the parameters needed for the instance/solver.
 *
 * @property instanceName String: Name of instance file with .txt extension
 * @property instancePath String: Local path to the folder containing the instance file
 * @property timeLimitInSeconds Int: Maximum amount of time the solver can run for
 * @property eps Constant value used as the tolerance for comparing double values
 * @property numBits Constant value representing the number of bits of the operating system
 */

object Parameters
{
    /**
     * Name of instance file with .txt extension
     */
    var instanceName: String = "p2.2.a.txt"
        private set

    /**
     * Path to the folder containing the instance file
     */
    var instancePath: String = "./data/Set_21_234/"
        private set

    /**
     * Maximum amount of time the solver can run for
     */
    var timeLimitInSeconds: Int = 3600
        private set

    /**
     * Tolerance used for comparing double values
     */
    const val eps = 1e-5

    /**
     * Number of bits of the operation system.
     */
    const val numBits = 64

    fun initialize(
        instanceName: String,
        instancePath: String,
        timeLimitInSeconds: Int
    )
    {
        Parameters.instanceName = instanceName
        Parameters.instancePath = instancePath
        Parameters.timeLimitInSeconds = timeLimitInSeconds
    }

}