package graphics.scenery.tests.interfaces

import graphics.scenery.Camera
import graphics.scenery.FullscreenObject
import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase
import graphics.scenery.parallelization.ParallelizationBase
import graphics.scenery.parallelization.TestParallelization

class NaiveInterface(wWidth: Int, wHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : RenderingInterfaceBase("NaiveInterface", wWidth, wHeight, rank, commSize, nodeRank) {

    override fun initializeParallelizationScheme(camera: Camera): ParallelizationBase {
        return TestParallelization(volumeManagerManager, mpiParameters, camera)
    }

    override fun setupVolumeManagerManager() {
        volumeManagerManager = VolumeManagerManager(hub)
        volumeManagerManager.instantiateVolumeManager(VolumeManagerManager.OutputType.REGULAR_IMAGE,
            windowWidth, windowHeight, scene)
    }

    override fun additionalSceneSetup() {
        val plane = FullscreenObject()
        scene.addChild(plane)
        plane.material().textures["diffuse"] = volumeManagerManager.getColorTextureOrNull()!!
    }

    override fun runAsynchronously() {
        while (renderer?.shouldClose == false) {
            renderer?.screenshot("naive_interface_output.png")

            //sleep for 10 seconds
            Thread.sleep(10000)
        }
    }
}