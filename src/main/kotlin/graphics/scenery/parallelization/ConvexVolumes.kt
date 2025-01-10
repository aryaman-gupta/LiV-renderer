package graphics.scenery.parallelization

import graphics.scenery.VolumeManagerManager
import java.nio.ByteBuffer

class ConvexVolumes(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters) : ParallelizationBase (volumeManagerManager, mpiParameters) {

    override val twoPassRendering = false
    override val explicitCompositingStep = false

    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // call the ICET composite image function
    }


}