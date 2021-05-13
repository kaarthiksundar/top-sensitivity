package data

/*
    Making an object called Parameters. In Kotlin, an object is a singleton (i.e., a single instantiation of a class).
    By creating Parameters using "object", we are guaranteed that Parameters is thread-safe.
 */


/**
 * Singleton to hold all the parameters for the solver and the instance to be solved.
 */
object Parameters
{
    /**
     * File name of the instance including the .txt extension.
     */
    var instanceName: String = "p2.2.a.txt"
        private set

    /**
     * File path of the local folder containing the .txt for the problem instance.
     */
    var instancePath: String = "./data/Set_21_234/"
        private set

    /**
     * Maximum time (in seconds) for the solver.
     */
    var timeLimitInSeconds: Int = 600
        private set

    /**
     * Tolerance used for comparing double values.
     */
    const val eps = 1e-5

    /**
     * Number of bits of the operating system.
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