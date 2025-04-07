package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.Settings
import graphics.scenery.VolumeManagerManager
import graphics.scenery.natives.IceTWrapper
import graphics.scenery.utils.VideoEncoder
import java.nio.ByteBuffer

class NonConvexVolumesParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, camera: Camera) : ParallelizationBase (volumeManagerManager, mpiParameters, camera) {

    override val twoPassRendering = false
    override val explicitCompositingStep = false

    val nativeContext = IceTWrapper.createNativeContext()

    private lateinit var encoder: VideoEncoder

    init {
        IceTWrapper.setupICET(nativeContext, windowWidth, windowHeight)

        Settings().set("VideoEncoder.StreamVideo", true)
        Settings().set("VideoEncoder.StreamingAddress", "rtp://" + Settings().get("ServerAddress", "127.0.0.1").toString()
            .replaceFirst(Regex("^[a-zA-Z]+://"), "") + ":5004")
        encoder = VideoEncoder(windowWidth, windowHeight, "rtp://" + Settings().get("ServerAddress", "127.0.0.1").toString()
            .replaceFirst(Regex("^[a-zA-Z]+://"), "") + ":5004", networked = true)
    }

    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // call the ICET composite image function

        if (buffers.size != 2) {
            throw IllegalArgumentException("Expected exactly two buffers but got ${buffers.size}")
        }

        val compositedColors = IceTWrapper.compositeLayered(
            nativeContext,
            buffers[0],
            buffers[1],
            windowWidth,
            windowHeight,
            volumeManagerManager.NUM_LAYERS
        )

        if (isRootProcess()) {
            // put the composited colors into the final composited buffer list
            compositedColors?.let {
                finalBuffers.add(compositedColors)
            }
        }
    }

    override fun streamOutput() {
        encoder.encodeFrame(finalBuffers[0])
        videoStreamRunning = true
    }
}