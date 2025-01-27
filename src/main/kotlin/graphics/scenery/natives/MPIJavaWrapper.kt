package graphics.scenery.natives

object MPIJavaWrapper {
    // Make sure the library name here matches what you'll build
    // (e.g., libmpi_java_wrapper.so on Linux).
    init {
        System.loadLibrary("MPIJNIBridge")
    }

    /**
     * Initializes the MPI execution environment.
     *
     * @param args Command-line arguments typically passed to MPI.
     */
    external fun init(args: Array<String?>?)

    /**
     * Finalizes the MPI execution environment.
     */
    external fun finalizeMPI()

    /**
     * Gets the rank of the calling process in the communicator.
     *
     * @return The rank of the calling process.
     */
    external fun commRank(): Int

    /**
     * Gets the size of the group associated with a communicator.
     *
     * @return The number of processes.
     */
    external fun commSize(): Int

    /**
     * Sends a byte array to a destination process with a specific tag.
     *
     * @param data The data to send.
     * @param dest The rank of the destination process.
     * @param tag  The message tag.
     */
    external fun send(data: ByteArray?, dest: Int, tag: Int)

    /**
     * Receives a byte array from a source process with a specific tag.
     * The return value is a newly allocated byte array containing the message data.
     *
     * @param source The rank of the source process.
     * @param tag    The message tag.
     * @return The received data as a byte array.
     */
    external fun recv(source: Int, tag: Int): ByteArray?

    /**
     * Broadcasts a byte array from the process with rank 'root' to all other processes
     * in the communicator. The array is updated in place on non-root processes.
     *
     * @param data The byte array to broadcast (modified in place for non-root processes).
     * @param root The rank of the broadcast root.
     */
    external fun bcast(data: ByteArray?, root: Int)
}

