package branchandbound

class BestBoundComparator : Comparator<INode> {
    override fun compare(p0: INode, p1: INode): Int {
        var c = p0.lpObjective.compareTo(p1.lpObjective)
        if (c == 0) c = p0.id.compareTo(p1.id)
        return c
    }
}

class WorstBoundComparator : Comparator<INode> {
    override fun compare(p0: INode, p1: INode): Int {
        var c = -p0.lpObjective.compareTo(p1.lpObjective)
        if (c == 0) c = p0.id.compareTo(p1.id)
        return c
    }
}