package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.VolumeManagerManager
import graphics.scenery.natives.IceTWrapper
import org.joml.Vector3f
import java.nio.ByteBuffer

class ConvexVolumesParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, camera: Camera) : ParallelizationBase (volumeManagerManager, mpiParameters, camera) {

    override val twoPassRendering = false
    override val explicitCompositingStep = false

    val nativeContext = IceTWrapper.createNativeContext()

    init {
        IceTWrapper.setupICET(nativeContext, windowWidth, windowHeight)
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

        val cameraPosition = FloatArray(3)
        cameraPosition[0] = camera.spatial().position.x
        cameraPosition[1] = camera.spatial().position.y
        cameraPosition[2] = camera.spatial().position.z

        val compositedColors = IceTWrapper.compositeFrame(nativeContext, buffers[0], cameraPosition, windowWidth, windowHeight)

        if (isRootProcess()) {
            // put the composited colors into the final composited buffer list
            compositedColors?.let {
                finalCompositedBuffers.add(compositedColors)
            }
        }
    }
}