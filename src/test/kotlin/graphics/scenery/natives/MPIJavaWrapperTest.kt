package graphics.scenery.tests.graphics.scenery.natives

import graphics.scenery.natives.MPIJavaWrapper

object MPIJavaWrapperTest {
    @JvmStatic
    fun main(args: Array<String>) {
        // Initialize MPI
        MPIJavaWrapper.init(args as Array<String?>?)

        val rank: Int = MPIJavaWrapper.commRank()
        val size: Int = MPIJavaWrapper.commSize()

        println("Hello from rank " + rank + " out of " + size + " processes.")

        // Example usage of Send/Recv
        if (size > 1) {
            if (rank == 0) {
                val msg = "Hello from rank 0!"
                MPIJavaWrapper.send(msg.toByteArray(), 1, 99)
                println("Rank 0: Sent message to rank 1.")
            } else if (rank == 1) {
                val received: ByteArray = MPIJavaWrapper.recv(0, 99)!!
                val receivedStr = String(received)
                println("Rank 1: Received message: " + receivedStr)
            }
        }

        // Example usage of Bcast
        // We'll have the root fill the array, then broadcast it to everyone
        val broadcastData = ByteArray(16)
        if (rank == 0) {
            val testString = "BcastExample!"
            val temp = testString.toByteArray()
            System.arraycopy(temp, 0, broadcastData, 0, temp.size)
            println("Rank 0: Broadcasting data: " + testString)
        } else {
            // Fill with zeros or placeholders
            for (i in broadcastData.indices) {
                broadcastData[i] = 0
            }
        }

        // Perform the broadcast
        MPIJavaWrapper.bcast(broadcastData, 0)

        // Check results
        val afterBcast = String(broadcastData)
        println("Rank " + rank + " after Bcast: " + afterBcast)

        // Finalize MPI
        MPIJavaWrapper.finalizeMPI()
    }
}

