package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.VolumeManagerManager
import java.nio.ByteBuffer

class TestParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, camera: Camera)
    : ParallelizationBase("test", volumeManagerManager, mpiParameters, camera)
{

    override val twoPassRendering = false
    override val explicitCompositingStep = false

    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // simply add the color buffer to the final composited buffers
        if (buffers.size != 1) {
            throw IllegalArgumentException("Expected exactly one buffer but got ${buffers.size}")
        }

        finalCompositedBuffers.add(buffers[0])
    }

    override fun streamOutput() {
        // do nothing
    }
}