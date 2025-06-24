package graphics.scenery.tests.interfaces

import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase
import graphics.scenery.parallelization.DistributedVDIsParallelization
import graphics.scenery.parallelization.ParallelizationBase
import org.joml.Matrix4f
import org.joml.Vector3f

@Suppress("unused")
class VDIGenerationInterface(wWidth: Int, wHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : RenderingInterfaceBase("VDIGeneration", wWidth, wHeight, rank, commSize, nodeRank) {
    override var outputProcessingType = OutputProcessingType.SAVE_TO_DISK

    fun generateModelMatrix(): Matrix4f {
        val model = Matrix4f().identity()
        //TODO: generalize the position of the volume
        model.translate(0f, 0f, 0f)

        val localScale = Vector3f(
            pixelToWorld,
            -1.0f * pixelToWorld,
            pixelToWorld
        )

        model.scale(localScale)

        return model
    }

    override fun initializeParallelizationScheme(): ParallelizationBase {
        return DistributedVDIsParallelization(volumeManagerManager, mpiParameters, scene, volumeDimensions, generateModelMatrix())
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