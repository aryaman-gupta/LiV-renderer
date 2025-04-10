package graphics.scenery.natives

import java.nio.ByteBuffer

class VDIMPIWrapper {

    companion object {
        init {
            // Replace "vdi_mpi_wrapper" with whatever your native library is named:
            System.loadLibrary("vdi_mpi_wrapper")
        }
    }

    /**
     * Allocates a native struct (VDIResources) with buffers for color, depth, and prefix.
     * Returns a handle (pointer) to that struct as a Long.
     *
     * @param colorCapacity  how many bytes to allocate for color
     * @param depthCapacity  how many bytes to allocate for depth
     * @param prefixCapacity how many bytes to allocate for prefix
     */
    external fun initializeVDIResources(
        colorCapacity: Int,
        depthCapacity: Int,
        prefixCapacity: Int
    ): Long

    /**
     * Distributes color data via MPI, storing the result in the native color buffer,
     * and returning a ByteBuffer to that native memory.
     *
     * @param nativeHandle pointer to the VDIResources struct
     * @param colorVDI the local buffer you want to distribute
     * @param supersegmentCounts an array of supersegment counts
     * @param commSize the MPI communicator size
     * @return a ByteBuffer pointing to the distributed color data in native memory
     */
    external fun distributeColorVDI(
        nativeHandle: Long,
        colorVDI: ByteBuffer,
        supersegmentCounts: IntArray,
        commSize: Int
    ): ByteBuffer

    /**
     * Distributes depth data via MPI, storing the result in the native depth buffer,
     * and returning a ByteBuffer to that native memory.
     */
    external fun distributeDepthVDI(
        nativeHandle: Long,
        depthVDI: ByteBuffer,
        supersegmentCounts: IntArray,
        commSize: Int
    ): ByteBuffer

    /**
     * Distributes the prefix data (e.g., prefix sums) via MPI,
     * storing the result in the native prefix buffer,
     * and returning a ByteBuffer to that native memory.
     *
     */
    external fun distributePrefixVDI(
        nativeHandle: Long,
        prefixVDI: ByteBuffer,
        commSize: Int
    ): ByteBuffer

    /**
     * Frees the native struct and its buffers.
     */
    external fun releaseVDIResources(nativeHandle: Long)
}
