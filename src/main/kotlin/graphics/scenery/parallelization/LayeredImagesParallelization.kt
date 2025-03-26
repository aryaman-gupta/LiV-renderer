package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.Settings
import graphics.scenery.VolumeManagerManager
import graphics.scenery.natives.IceTWrapper
import graphics.scenery.utils.VideoEncoder
import graphics.scenery.utils.SystemHelpers
import java.nio.ByteBuffer

class LayeredImagesParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, camera: Camera)
    : ParallelizationBase ("layered", volumeManagerManager, mpiParameters, camera)
{

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

        if (saveCompositingInput) {
            SystemHelpers.dumpToFile(buffers[0], "$outDir/$frameNumber-${mpiParameters.rank}.color")
            SystemHelpers.dumpToFile(buffers[1], "$outDir/$frameNumber-${mpiParameters.rank}.depth")
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
                finalCompositedBuffers.add(compositedColors)
            }
        }
    }

    override fun streamOutput() {
        encoder.encodeFrame(finalCompositedBuffers[0])
        videoStreamRunning = true
    }
}