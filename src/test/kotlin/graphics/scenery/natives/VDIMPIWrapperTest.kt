package graphics.scenery.natives

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.lwjgl.system.MemoryUtil
import org.stringtemplate.v4.compiler.STParser

/**
 * Tests for the VDIMPIWrapper class.
 * 
 * These tests verify the functionality of the JNI bindings to MPI functions
 * used for distributed rendering. The tests are designed to work in both
 * single-process and multi-process environments.
 */
class VDIMPIWrapperTest {

    /**
     * Test initialization and resource allocation.
     * 
     * This test verifies that the initializeVDIResources method correctly
     * allocates resources and returns a valid handle.
     */
    @Test
    fun testInitializeResources() {
        // Initialize resources with small buffer sizes for testing
        val colorCapacity = 1024
        val depthCapacity = 512
        val prefixCapacity = 256

        val handle = VDIMPIWrapper.initializeVDIResources(colorCapacity, depthCapacity, prefixCapacity, colorCapacity, depthCapacity)

        // Verify that a valid handle is returned
        assertTrue(handle != 0L, "Handle should be non-zero")

        // Clean up resources
        VDIMPIWrapper.releaseVDIResources(handle)
    }

    /**
     * Test distribution of supersegment counts.
     * 
     * This test verifies that the distributeSupersegmentCounts method correctly
     * distributes supersegment counts via MPI_Alltoall.
     */
    @Test
    fun testDistributeSupersegmentCounts() {
        // Initialize resources
        val colorCapacity = 1024
        val depthCapacity = 512
        val prefixCapacity = 256
        val handle = VDIMPIWrapper.initializeVDIResources(colorCapacity, depthCapacity, prefixCapacity, colorCapacity, depthCapacity)

        val commSize = VDIMPIWrapper.getCommSize()

        // Create test data
        val supersegmentCounts = IntArray(commSize) { i -> (i + 1) * 10 }  // [10, 20]

        // Distribute supersegment counts
        val receivedCounts = VDIMPIWrapper.distributeSupersegmentCounts(handle, supersegmentCounts, commSize)

        // In a single-process environment, we expect to receive our own counts
        // In a multi-process environment, we would receive counts from other processes
        assertNotNull(receivedCounts, "Received counts should not be null")
        assertEquals(commSize, receivedCounts.size, "Received counts array should have the same size as commSize")

        // Print debug information
        println("[DEBUG_LOG] Sent counts: ${supersegmentCounts.joinToString()}")
        println("[DEBUG_LOG] Received counts: ${receivedCounts.joinToString()}")

        // Clean up resources
        VDIMPIWrapper.releaseVDIResources(handle)
    }

    /**
     * Test distribution of color data.
     * 
     * This test verifies that the distributeColorVDI method correctly
     * distributes color data via MPI_Alltoallv.
     */
    @Test
    fun testDistributeColorVDI() {
        // Initialize resources
        val colorCapacity = 1024
        val depthCapacity = 512
        val prefixCapacity = 256
        val handle = VDIMPIWrapper.initializeVDIResources(colorCapacity, depthCapacity, prefixCapacity, colorCapacity, depthCapacity)

        val commSize = VDIMPIWrapper.getCommSize()

        // Create test data
        val supersegmentCounts = IntArray(commSize) { i -> (i + 1) * 10 }  // [10, 20]

        // Distribute supersegment counts first (required before other MPI communication)
        val receivedCounts = VDIMPIWrapper.distributeSupersegmentCounts(handle, supersegmentCounts, commSize)

        // Create color buffer with test data
        val colorBufferSize = supersegmentCounts.sum() * 4 * 4  // 4 floats per color, 4 bytes per float
        val colorBuffer = MemoryUtil.memAlloc(colorBufferSize)
        val colorFloatBuffer = colorBuffer.asFloatBuffer()

        // Fill buffer with test data
        for (i in 0 until colorFloatBuffer.capacity()) {
            colorFloatBuffer.put(i, i.toFloat())
        }

        // Calculate counts and displacements for MPI_Alltoallv
        val colorCounts = IntArray(commSize)
        val colorDisplacements = IntArray(commSize)
        var colorDisplacementSum = 0

        for (i in 0 until commSize) {
            colorCounts[i] = supersegmentCounts[i] * 4 * 4
            colorDisplacements[i] = colorDisplacementSum
            colorDisplacementSum += colorCounts[i]
        }

        // Calculate receive counts and displacements
        val colorCountsRecv = IntArray(commSize)
        val colorDisplacementsRecv = IntArray(commSize)
        var colorDisplacementRecvSum = 0

        for (i in 0 until commSize) {
            val count: Int = receivedCounts[i]
            colorCountsRecv[i] = count * 4 * 4
            colorDisplacementsRecv[i] = colorDisplacementRecvSum
            colorDisplacementRecvSum += colorCountsRecv[i]
        }

        // Distribute color data
        val distributedColors = VDIMPIWrapper.distributeColorVDI(
            handle,
            colorBuffer,
            colorCounts,
            colorDisplacements,
            colorCountsRecv,
            colorDisplacementsRecv,
            commSize
        )

        // Verify that a valid buffer is returned
        assertNotNull(distributedColors, "Distributed colors buffer should not be null")

//        assertEquals(0, colorBufferSize, "Color buffer size should match the expected size")

        // Print debug information
        println("[DEBUG_LOG] Color buffer size: $colorBufferSize")
        println("[DEBUG_LOG] Distributed colors buffer size: ${distributedColors.capacity()}")

        // Clean up resources
        MemoryUtil.memFree(colorBuffer)
        VDIMPIWrapper.releaseVDIResources(handle)
    }

    /**
     * Test distribution of depth data.
     * 
     * This test verifies that the distributeDepthVDI method correctly
     * distributes depth data via MPI_Alltoallv.
     */
    @Test
    fun testDistributeDepthVDI() {
        // Initialize resources
        val colorCapacity = 1024
        val depthCapacity = 512
        val prefixCapacity = 256
        val handle = VDIMPIWrapper.initializeVDIResources(colorCapacity, depthCapacity, prefixCapacity, colorCapacity, depthCapacity)

        val commSize = VDIMPIWrapper.getCommSize()

        // Create test data
        val supersegmentCounts = IntArray(commSize) { i -> (i + 1) * 10 }  // [10, 20]

        // Distribute supersegment counts first (required before other MPI communication)
        val receivedCounts = VDIMPIWrapper.distributeSupersegmentCounts(handle, supersegmentCounts, commSize)

        // Create depth buffer with test data
        val depthBufferSize = supersegmentCounts.sum() * 4 * 2  // 4 floats per depth, 2 bytes per float (assuming half-precision)
        val depthBuffer = MemoryUtil.memAlloc(depthBufferSize)

        // Fill buffer with test data
        for (i in 0 until depthBufferSize) {
            depthBuffer.put(i, i.toByte())
        }

        // Calculate counts and displacements for MPI_Alltoallv
        val depthCounts = IntArray(commSize)
        val depthDisplacements = IntArray(commSize)
        var depthDisplacementSum = 0

        for (i in 0 until commSize) {
            depthCounts[i] = supersegmentCounts[i] * 4 * 2
            depthDisplacements[i] = depthDisplacementSum
            depthDisplacementSum += depthCounts[i]
        }

        // Calculate receive counts and displacements
        val depthCountsRecv = IntArray(commSize)
        val depthDisplacementsRecv = IntArray(commSize)
        var depthDisplacementRecvSum = 0

        for (i in 0 until commSize) {
            val count: Int = receivedCounts[i]
            depthCountsRecv[i] = count * 4 * 2
            depthDisplacementsRecv[i] = depthDisplacementRecvSum
            depthDisplacementRecvSum += depthCountsRecv[i]
        }

        // Distribute depth data
        val distributedDepths = VDIMPIWrapper.distributeDepthVDI(
            handle,
            depthBuffer,
            depthCounts,
            depthDisplacements,
            depthCountsRecv,
            depthDisplacementsRecv,
            commSize
        )

        // Verify that a valid buffer is returned
        assertNotNull(distributedDepths, "Distributed depths buffer should not be null")

        // Print debug information
        println("[DEBUG_LOG] Depth buffer size: $depthBufferSize")
        println("[DEBUG_LOG] Distributed depths buffer size: ${distributedDepths.capacity()}")

        // Clean up resources
        MemoryUtil.memFree(depthBuffer)
        VDIMPIWrapper.releaseVDIResources(handle)
    }

    /**
     * Test distribution of prefix data.
     * 
     * This test verifies that the distributePrefixVDI method correctly
     * distributes prefix data via MPI_Alltoall.
     */
    @Test
    fun testDistributePrefixVDI() {
        // Initialize resources
        val colorCapacity = 1024
        val depthCapacity = 512
        val prefixCapacity = 256
        val handle = VDIMPIWrapper.initializeVDIResources(colorCapacity, depthCapacity, prefixCapacity, colorCapacity, depthCapacity)

        val commSize = VDIMPIWrapper.getCommSize()

        // Create test data

        // Create prefix buffer with test data
        val prefixBufferSize = 128 * commSize  // Size must be divisible by commSize for MPI_Alltoall
        val prefixBuffer = MemoryUtil.memAlloc(prefixBufferSize)
        val prefixIntBuffer = prefixBuffer.asIntBuffer()

        // Fill buffer with test data
        for (i in 0 until prefixIntBuffer.capacity()) {
            prefixIntBuffer.put(i, i)
        }

        // Distribute prefix data
        val distributedPrefix = VDIMPIWrapper.distributePrefixVDI(handle, prefixBuffer, commSize)

        // Verify that a valid buffer is returned
        assertNotNull(distributedPrefix, "Distributed prefix buffer should not be null")

        // Print debug information
        println("[DEBUG_LOG] Prefix buffer size: $prefixBufferSize")
        println("[DEBUG_LOG] Distributed prefix buffer size: ${distributedPrefix.capacity()}")

        // Clean up resources
        MemoryUtil.memFree(prefixBuffer)
        VDIMPIWrapper.releaseVDIResources(handle)
    }

    @Test
    fun distributeEqualPartitions() {
        val windowWidth = 100
        val windowHeight = 100
        val maxSupersegments = 10

        val colorCapacity = windowWidth * windowHeight * 4 * maxSupersegments * 4
        val depthCapacity = windowWidth * windowHeight * 2 * maxSupersegments * 2
        val prefixCapacity = windowWidth * windowHeight * 4

        val handle = VDIMPIWrapper.initializeVDIResources(colorCapacity, depthCapacity, prefixCapacity, colorCapacity, depthCapacity)
        val commSize = VDIMPIWrapper.getCommSize()

        // assuming all VDIs are full and there is no empty space (effectively no run-length compression)
        val totalSupersegments = windowWidth * windowHeight * maxSupersegments
        val supersegmentCounts = IntArray(commSize) { totalSupersegments / commSize }

        val receiveCounts = VDIMPIWrapper.distributeSupersegmentCounts(handle, supersegmentCounts, commSize)

        assertNotNull(receiveCounts, "Received counts should not be null")
        assertEquals(commSize, receiveCounts.size, "Received counts array should have the same size as commSize")
        assertEquals(receiveCounts[0], supersegmentCounts[0], "Received counts should match sent counts")

        val colorBuffer = MemoryUtil.memAlloc(colorCapacity)
        val depthBuffer = MemoryUtil.memAlloc(depthCapacity)

        val colorCounts = IntArray(commSize)
        val colorDisplacements = IntArray(commSize)
        var colorDisplacementSum = 0
        val depthCounts = IntArray(commSize)
        val depthDisplacements = IntArray(commSize)
        var depthDisplacementSum = 0

        val colorReceiveCounts = IntArray(commSize)
        val colorReceiveDisplacements = IntArray(commSize)
        val depthReceiveCounts = IntArray(commSize)
        val depthReceiveDisplacements = IntArray(commSize)

        var colorReceiveDisplacementSum = 0
        var depthReceiveDisplacementSum = 0

        for (i in 0 until commSize) {
            colorCounts[i] = supersegmentCounts[i] * 4 * 4
            colorDisplacements[i] = colorDisplacementSum
            colorDisplacementSum += colorCounts[i]
            depthCounts[i] = supersegmentCounts[i] * 4 * 2
            depthDisplacements[i] = depthDisplacementSum
            depthDisplacementSum += depthCounts[i]

            colorReceiveCounts[i] = receiveCounts[i] * 4 * 4
            depthReceiveCounts[i] = receiveCounts[i] * 4 * 2
            colorReceiveDisplacements[i] = colorReceiveDisplacementSum
            depthReceiveDisplacements[i] = depthReceiveDisplacementSum
            colorReceiveDisplacementSum += colorReceiveCounts[i]
            depthReceiveDisplacementSum += depthReceiveCounts[i]
        }

        val receivedColors = VDIMPIWrapper.distributeColorVDI(
            handle,
            colorBuffer,
            colorCounts,
            colorDisplacements,
            colorReceiveCounts,
            colorReceiveDisplacements,
            commSize
        )

        val receivedDepths = VDIMPIWrapper.distributeDepthVDI(
            handle,
            depthBuffer,
            depthCounts,
            depthDisplacements,
            depthReceiveCounts,
            depthReceiveDisplacements,
            commSize
        )

        //since we are distributing "full" VDIs, we expect the received buffers to be the same size as the original buffers
        assertEquals(colorBuffer.capacity(), receivedColors.capacity(), "Received color buffer size should match original")
        assertEquals(depthBuffer.capacity(), receivedDepths.capacity(), "Received depth buffer size should match original")
    }

    /**
     * Test resource cleanup.
     * 
     * This test verifies that the releaseVDIResources method correctly
     * cleans up allocated resources.
     */
    @Test
    fun testReleaseResources() {
        // Initialize resources
        val colorCapacity = 1024
        val depthCapacity = 512
        val prefixCapacity = 256
        val handle = VDIMPIWrapper.initializeVDIResources(colorCapacity, depthCapacity, prefixCapacity, colorCapacity, depthCapacity)

        // Verify that a valid handle is returned
        assertTrue(handle != 0L, "Handle should be non-zero")

        // Release resources
        VDIMPIWrapper.releaseVDIResources(handle)

        // No assertion needed here, as we're just verifying that the method doesn't crash
        // In a real test, we might want to check for memory leaks or other side effects
    }
}

