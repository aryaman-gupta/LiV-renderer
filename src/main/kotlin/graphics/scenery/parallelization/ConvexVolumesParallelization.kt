package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.Scene
import graphics.scenery.Settings
import graphics.scenery.VolumeManagerManager
import graphics.scenery.natives.IceTWrapper
import graphics.scenery.utils.VideoEncoder
import org.joml.Vector3f
import java.nio.ByteBuffer

class ConvexVolumesParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, scene: Scene) : ParallelizationBase (volumeManagerManager, mpiParameters, scene) {

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

    fun setCentroids(centroids: MutableMap<Int, Vector3f>) {
        centroids.forEach { (processorID, positions) ->
            IceTWrapper.setProcessorCentroid(nativeContext, processorID, floatArrayOf(positions.x, positions.y, positions.z))        }
    }

    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // call the ICET composite image function

        if (buffers.size != 1) {
            throw IllegalArgumentException("Expected exactly one buffer but got ${buffers.size}")
        }

        if(scene.findObserver() == null) {
            throw IllegalStateException("No camera found in scene.")
        }

        val camera = scene.findObserver() as Camera

        val cameraPosition = FloatArray(3)
        cameraPosition[0] = camera.spatial().position.x
        cameraPosition[1] = camera.spatial().position.y
        cameraPosition[2] = camera.spatial().position.z

        val compositedColors = IceTWrapper.compositeFrame(nativeContext, buffers[0], cameraPosition, windowWidth, windowHeight)

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