package graphics.scenery.natives

import java.nio.ByteBuffer

/**
 * Native wrapper for MPI operations used in VDI rendering.
 * This class provides JNI bindings to MPI functions for distributed rendering.
 */
object VDIMPIWrapper {
    init {
        System.loadLibrary("vdi_mpi_wrapper")
    }

    /**
     * Initialize VDI resources for MPI communication.
     *
     * @param colorCapacity Maximum size of the color buffer in bytes
     * @param depthCapacity Maximum size of the depth buffer in bytes
     * @param prefixCapacity Maximum size of the prefix buffer in bytes
     * @return Native handle to the allocated resources
     */
    external fun initializeVDIResources(colorCapacity: Int, depthCapacity: Int, prefixCapacity: Int): Long

    /**
     * Distribute supersegment counts via MPI_Alltoall.
     * This must be called before any other MPI communication.
     *
     * @param nativeHandle Handle to the native resources
     * @param supersegmentCounts Array of supersegment counts to distribute
     * @param commSize Size of the MPI communicator
     * @return Array of received supersegment counts
     */
    external fun distributeSupersegmentCounts(nativeHandle: Long, supersegmentCounts: IntArray, commSize: Int): IntArray

    /**
     * Distribute color VDI data via MPI_Alltoallv.
     *
     * @param nativeHandle Handle to the native resources
     * @param colorVDI ByteBuffer containing the color data to distribute
     * @param counts Array of send counts for each process
     * @param displacements Array of send displacements for each process
     * @param countsRecv Array of receive counts for each process
     * @param displacementsRecv Array of receive displacements for each process
     * @param commSize Size of the MPI communicator
     * @return ByteBuffer containing the received color data
     */
    external fun distributeColorVDI(
        nativeHandle: Long,
        colorVDI: ByteBuffer,
        counts: IntArray,
        displacements: IntArray,
        countsRecv: IntArray,
        displacementsRecv: IntArray,
        commSize: Int
    ): ByteBuffer

    /**
     * Distribute depth VDI data via MPI_Alltoallv.
     *
     * @param nativeHandle Handle to the native resources
     * @param depthVDI ByteBuffer containing the depth data to distribute
     * @param counts Array of send counts for each process
     * @param displacements Array of send displacements for each process
     * @param countsRecv Array of receive counts for each process
     * @param displacementsRecv Array of receive displacements for each process
     * @param commSize Size of the MPI communicator
     * @return ByteBuffer containing the received depth data
     */
    external fun distributeDepthVDI(
        nativeHandle: Long,
        depthVDI: ByteBuffer,
        counts: IntArray,
        displacements: IntArray,
        countsRecv: IntArray,
        displacementsRecv: IntArray,
        commSize: Int
    ): ByteBuffer

    /**
     * Distribute prefix VDI data via MPI_Alltoall.
     *
     * @param nativeHandle Handle to the native resources
     * @param prefixVDI ByteBuffer containing the prefix data to distribute
     * @param commSize Size of the MPI communicator
     * @return ByteBuffer containing the received prefix data
     */
    external fun distributePrefixVDI(nativeHandle: Long, prefixVDI: ByteBuffer, commSize: Int): ByteBuffer

    /**
     * Retrieves the size of the MPI communicator.
     *
     * @return The number of processes in the MPI communicator.
     */
    @JvmStatic
    external fun getCommSize(): Int

    /**
     * Release native resources.
     *
     * @param nativeHandle Handle to the native resources to release
     */
    external fun releaseVDIResources(nativeHandle: Long)
}

