package graphics.scenery.parallelization

import graphics.scenery.VolumeManagerManager
import graphics.scenery.natives.IceTWrapper
import java.nio.ByteBuffer

class ConvexVolumesParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters) : ParallelizationBase (volumeManagerManager, mpiParameters) {

    override val twoPassRendering = false
    override val explicitCompositingStep = false

    val nativeContext = IceTWrapper.createNativeContext()

    init {
//        IceTWrapper.setupICET()
    }


    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // call the ICET composite image function
//        IceTWrapper.compositeFrame()
    }

}