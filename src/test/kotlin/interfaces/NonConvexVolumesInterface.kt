package graphics.scenery.tests.interfaces

import graphics.scenery.Camera
import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase
import graphics.scenery.parallelization.NonConvexVolumesParallelization
import graphics.scenery.parallelization.ParallelizationBase
import org.joml.Vector3f

@Suppress("unused")
class NonConvexVolumesInterface(wWidth: Int, wHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : RenderingInterfaceBase("NonConvexVolumes", wWidth, wHeight, rank, commSize, nodeRank) {

    override var outputProcessingType = OutputProcessingType.DISPLAY

    override fun initializeParallelizationScheme(camera: Camera): ParallelizationBase {
        return NonConvexVolumesParallelization(volumeManagerManager, mpiParameters, camera)
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