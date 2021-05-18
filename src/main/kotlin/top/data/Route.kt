package top.data

/**
 * Route data of a vehicle.
 *
 * @param path sequence of vertices.
 * @param score sum of scores of vertices visited by route.
 * @param length sum of lengths of edges on route.
 */
data class Route(
    val path: List<Int>,
    val score: Double,
    val length: Double,
) {
    override fun equals(other: Any?): Boolean {
        return other != null && (other is Route) && path == other.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}