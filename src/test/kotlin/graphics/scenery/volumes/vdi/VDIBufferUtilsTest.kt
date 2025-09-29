import graphics.scenery.parallelization.MPIParameters
import graphics.scenery.volumes.vdi.VDINode
import graphics.scenery.volumes.vdi.modifyFinalBuffersImpl
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class VDIBufferUtilsTest {
    @Test
    fun testModifyFinalBuffers_interleavesBuffersCorrectly() {
        val vdiWidth = 4
        val vdiHeight = 2
        val numSupersegments = 1
        val commSize = 2
        val mpiParameters = MPIParameters(rank = 0, commSize = commSize, nodeRank = 0)

        // Use VDINode static functions for type and channels
        val colorType = VDINode.getColorTextureType()
        val colorChannels = VDINode.getColorTextureChannels()
        val depthType = VDINode.getDepthTextureType()
        val depthChannels = VDINode.getDepthTextureChannels()
        val colorBytesPerChannel = when (colorType) {
            is net.imglib2.type.numeric.real.FloatType -> 4
            is net.imglib2.type.numeric.integer.UnsignedByteType -> 1
            is net.imglib2.type.numeric.integer.UnsignedShortType -> 2
            else -> 4 // fallback
        }
        val depthBytesPerChannel = when (depthType) {
            is net.imglib2.type.numeric.real.FloatType -> 4
            is net.imglib2.type.numeric.integer.UnsignedByteType -> 1
            is net.imglib2.type.numeric.integer.UnsignedShortType -> 2
            else -> 4 // fallback
        }
        val colorSupersegmentResolution = colorChannels * colorBytesPerChannel
        val depthSupersegmentResolution = depthChannels * depthBytesPerChannel
        val colorBufferSize = vdiWidth * vdiHeight * numSupersegments * colorSupersegmentResolution
        val depthBufferSize = vdiWidth * vdiHeight * numSupersegments * depthSupersegmentResolution

        // Simulate two sub-buffers for color
        val colorSubA = ByteArray(colorBufferSize / 2) { (1).toByte() }
        val colorSubB = ByteArray(colorBufferSize / 2) { (2).toByte() }
        val colorConcatenated = colorSubA + colorSubB

        // Simulate two sub-buffers for depth
        val depthSubA = ByteArray(depthBufferSize / 2) { (1).toByte() }
        val depthSubB = ByteArray(depthBufferSize / 2) { (2).toByte() }
        val depthConcatenated = depthSubA + depthSubB

        // Prepare buffers: metadata, color, depth
        val metadata = ByteBuffer.allocateDirect(4).putInt(42).rewind() as ByteBuffer //just random data, not used in test
        val color = ByteBuffer.allocateDirect(colorBufferSize).put(colorConcatenated).rewind() as ByteBuffer
        val depth = ByteBuffer.allocateDirect(depthBufferSize).put(depthConcatenated).rewind() as ByteBuffer
        val buffers = listOf(metadata, color, depth)

        // Call the utility function with static function references
        modifyFinalBuffersImpl(
            buffers,
            mpiParameters,
            vdiWidth,
            vdiHeight,
            numSupersegments
        )

        // Compute expected interleaved output for color (in chunks)
        val expectedColor = ByteArray(colorBufferSize)
        val colorSubSize = colorBufferSize / 2
        var colorOutIdx = 0
        var colorInIdx = 0
        while (colorInIdx < colorSubSize) {
            // Copy chunk from subA
            System.arraycopy(colorSubA, colorInIdx, expectedColor, colorOutIdx, colorSupersegmentResolution * vdiWidth / commSize)
            colorOutIdx += colorSupersegmentResolution * vdiWidth / commSize
            // Copy chunk from subB
            System.arraycopy(colorSubB, colorInIdx, expectedColor, colorOutIdx, colorSupersegmentResolution * vdiWidth / commSize)
            colorOutIdx += colorSupersegmentResolution * vdiWidth / commSize
            colorInIdx += colorSupersegmentResolution * vdiWidth / commSize
        }
        // Compute expected interleaved output for depth (in chunks)
        val expectedDepth = ByteArray(depthBufferSize)
        val depthSubSize = depthBufferSize / 2
        var depthOutIdx = 0
        var depthInIdx = 0
        while (depthInIdx < depthSubSize) {
            System.arraycopy(depthSubA, depthInIdx, expectedDepth, depthOutIdx, depthSupersegmentResolution * vdiWidth / commSize)
            depthOutIdx += depthSupersegmentResolution * vdiWidth / commSize
            System.arraycopy(depthSubB, depthInIdx, expectedDepth, depthOutIdx, depthSupersegmentResolution * vdiWidth / commSize)
            depthOutIdx += depthSupersegmentResolution * vdiWidth / commSize
            depthInIdx += depthSupersegmentResolution * vdiWidth / commSize
        }

        val actualColor = ByteArray(colorBufferSize)
        val actualDepth = ByteArray(depthBufferSize)
        buffers[1].get(actualColor)
        buffers[2].get(actualDepth)
        assertArrayEquals(expectedColor, actualColor, "Color buffer not interleaved correctly")
        assertArrayEquals(expectedDepth, actualDepth, "Depth buffer not interleaved correctly")
    }
}
