package graphics.scenery.volumes.vdi

import graphics.scenery.parallelization.MPIParameters
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.FloatType
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * Utility function to correct the linearization of VDI buffers.
 * This is the core logic extracted from DistributedVDIsParallelization.modifyFinalBuffers.
 */
fun modifyFinalBuffersImpl(
    buffers: List<ByteBuffer>,
    mpiParameters: MPIParameters,
    windowWidth: Int,
    windowHeight: Int,
    numSupersegments: Int
) {
    val logger = LoggerFactory.getLogger("VDIBufferUtils")
    if (buffers.size < 3) {
        logger.warn("Skipping switching of linearization of final buffers since only ${buffers.size} buffers are present")
        return
    }
    val colorBuffer = buffers[1]
    val depthBuffer = buffers[2]

    // Color buffer correction
    val colorBytesPerChannel = when(VDINode.getColorTextureType()) {
        FloatType() -> 4
        UnsignedByteType() -> 1
        else -> {
            logger.error("Unsupported color texture type: ${VDINode.getColorTextureType()}. Assuming 4 bytes per channel.")
            4
        }
    }
    correctLinearization(
        colorBuffer, windowWidth, windowHeight, numSupersegments,
        VDINode.getColorTextureChannels() * colorBytesPerChannel, mpiParameters, logger
    )

    // Depth buffer correction
    val depthBytesPerChannel = when(VDINode.getDepthTextureType()) {
        FloatType() -> 4
        UnsignedShortType() -> 2
        else -> {
            logger.error("Unsupported depth texture type: ${VDINode.getDepthTextureType()}. Assuming 4 bytes per channel.")
            4
        }
    }
    correctLinearization(
        depthBuffer, windowWidth, windowHeight, numSupersegments,
        VDINode.getDepthTextureChannels() * depthBytesPerChannel, mpiParameters, logger
    )
}

private fun correctLinearization(
    buffer: ByteBuffer,
    vdiWidth: Int,
    vdiHeight: Int,
    numSupersegments: Int,
    supersegmentResolution: Int,
    mpiParameters: MPIParameters,
    logger: org.slf4j.Logger
) {
    val commSize = mpiParameters.commSize
    val separatedBuffers = Array(commSize) { ByteBuffer.allocateDirect(buffer.remaining() / commSize) }

    val vdiSize = vdiWidth * vdiHeight * numSupersegments * supersegmentResolution
    if(vdiSize != buffer.remaining()) {
        logger.error("Buffer size mismatch in correctLinearization. Expected $vdiSize, got ${buffer.remaining()}")
        return
    }

    for(i in 0 until commSize) {
        val slice = ByteBuffer.allocateDirect(vdiSize / commSize)
        val oldLimit = buffer.limit()
        buffer.limit(buffer.position() + vdiSize / commSize)
        slice.put(buffer)
        slice.flip()
        separatedBuffers[i] = slice
        buffer.limit(oldLimit)
    }
    buffer.rewind()

    for(i in 0 until vdiSize step supersegmentResolution) {
        val x_ = i % (vdiWidth * supersegmentResolution)
        val sliceID = x_ / ceil(((vdiWidth.toFloat()) * supersegmentResolution) / commSize).toInt()
        val chunk = ByteArray(supersegmentResolution)
        separatedBuffers[sliceID].get(chunk)
        buffer.put(chunk)
    }
    buffer.rewind()
}

