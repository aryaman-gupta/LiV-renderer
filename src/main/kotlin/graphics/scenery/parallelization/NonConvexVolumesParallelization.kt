package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.VolumeManagerManager
import graphics.scenery.natives.IceTWrapper
import org.joml.Vector3f
import java.nio.ByteBuffer

class NonConvexVolumesParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, camera: Camera) : ParallelizationBase (volumeManagerManager, mpiParameters, camera) {

    override val twoPassRendering = false
    override val explicitCompositingStep = false

    val nativeContext = IceTWrapper.createNativeContext()

    init {
        IceTWrapper.setupICET(nativeContext, windowWidth, windowHeight)
    }

    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // call the ICET composite image function

        if (buffers.size != 2) {
            throw IllegalArgumentException("Expected exactly two buffers but got ${buffers.size}")
        }

        val compositedColors = IceTWrapper.compositeLayered(nativeContext, buffers[0], buffers[1], windowWidth, windowHeight, 2)

        if (isRootProcess()) {
            // put the composited colors into the final composited buffer list
            compositedColors?.let {
                finalCompositedBuffers.add(compositedColors)
            }
        }
    }
}