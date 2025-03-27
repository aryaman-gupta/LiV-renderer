package graphics.scenery.tests.interfaces

import graphics.scenery.Camera
import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase
import graphics.scenery.parallelization.DistributedVDIsParallelization
import graphics.scenery.parallelization.ParallelizationBase

@Suppress("unused")
class VDIGenerationInterface(wWidth: Int, wHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : RenderingInterfaceBase("VDIGeneration", wWidth, wHeight, rank, commSize, nodeRank) {
    override var outputProcessingType = OutputProcessingType.SAVE_TO_DISK

    override fun initializeParallelizationScheme(camera: Camera): ParallelizationBase {
        return DistributedVDIsParallelization(volumeManagerManager, mpiParameters, camera)
    }

    override fun setupVolumeManagerManager() {
        volumeManagerManager = VolumeManagerManager(hub)
        volumeManagerManager.instantiateVolumeManager(
            VolumeManagerManager.OutputType.VDI,
            windowWidth, windowHeight,
            scene
        )
    }
}