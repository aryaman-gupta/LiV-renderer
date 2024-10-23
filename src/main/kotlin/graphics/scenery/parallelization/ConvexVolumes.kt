package graphics.scenery.parallelization

import graphics.scenery.VolumeManagerManager

class ConvexVolumes(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters) : ParallelizationBase (volumeManagerManager, mpiParameters) {

    override val twoPassRendering = false


}