package graphics.scenery.tests.interfaces

import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase
import graphics.scenery.parallelization.NonConvexVolumesParallelization
import graphics.scenery.parallelization.ParallelizationBase

@Suppress("unused")
class NonConvexVolumesInterface(wWidth: Int, wHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : RenderingInterfaceBase("NonConvexVolumes", wWidth, wHeight, rank, commSize, nodeRank) {

    override var outputProcessingType = OutputProcessingType.DISPLAY

    override fun initializeParallelizationScheme(): ParallelizationBase {
        return NonConvexVolumesParallelization(volumeManagerManager, mpiParameters, scene)
    }

    override fun setupVolumeManagerManager() {
        volumeManagerManager = VolumeManagerManager(hub)
        volumeManagerManager.instantiateVolumeManager(
            VolumeManagerManager.OutputType.LAYERED_IMAGE,
            windowWidth, windowHeight,
            scene
        )
    }
}