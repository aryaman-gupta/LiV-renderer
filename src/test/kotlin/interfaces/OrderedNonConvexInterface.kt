package graphics.scenery.tests.interfaces

import graphics.scenery.Camera
import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase
import graphics.scenery.parallelization.ConvexVolumesParallelization
import graphics.scenery.parallelization.ParallelizationBase

@Suppress("unused")
class OrderedNonConvexInterface(wWidth: Int, wHeight: Int, rank: Int, commSize: Int, nodeRank: Int)
    : RenderingInterfaceBase("OrderedNonConvex", wWidth, wHeight, rank, commSize, nodeRank)
{
    override var outputProcessingType = OutputProcessingType.SAVE_TO_DISK

    override fun initializeParallelizationScheme(camera: Camera): ParallelizationBase {
        return ConvexVolumesParallelization(volumeManagerManager, mpiParameters, camera)
    }

    override fun setupVolumeManagerManager() {
        volumeManagerManager = VolumeManagerManager(hub)
        volumeManagerManager.instantiateVolumeManager(
            VolumeManagerManager.OutputType.REGULAR_IMAGE,
            windowWidth, windowHeight,
            scene
        )
    }
}