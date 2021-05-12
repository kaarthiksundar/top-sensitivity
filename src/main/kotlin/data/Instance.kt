package data

import data.SetGraph
import java.io.File


/*

    Class responsible for storing the relevant information for a problem instance.

 */

/*
 TODO: Learn how the instances are written in the text file
 TODO: Determine what parameters need to be stored for each instance
 TODO: Learn how to read and parse the .txt file for a given instance
 TODO: Build the graph from the data in the .txt file
*/

class Instance(
    private val name: String,
    private val path: String
)
{
    // Reading the text file and storing as a List of Strings. Each entry corresponds to a line in sequential order
    private var lines = File(path + name).readLines()


    init{

    }
}
