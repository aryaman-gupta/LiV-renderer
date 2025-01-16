package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.VolumeManagerManager
import java.nio.ByteBuffer

class TestParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, camera: Camera) : ParallelizationBase(volumeManagerManager, mpiParameters, camera) {

    override val twoPassRendering = false
    override val explicitCompositingStep = false

    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // do nothing
    }
}