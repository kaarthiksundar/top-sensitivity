package branchandbound

interface ISolver {
    fun solve(unsolvedNode: INode): INode
}