package graphics.scenery.tests.interfaces

import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase
import graphics.scenery.parallelization.ParallelizationBase
import graphics.scenery.parallelization.TestParallelization

class NaiveInterface(wWidth: Int, wHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : RenderingInterfaceBase("NaiveInterface", wWidth, wHeight, rank, commSize, nodeRank) {

    override var outputProcessingType = OutputProcessingType.DISPLAY

    override fun initializeParallelizationScheme(): ParallelizationBase {
        return TestParallelization(volumeManagerManager, mpiParameters, scene)
    }

    override fun setupVolumeManagerManager() {
        volumeManagerManager = VolumeManagerManager(hub)
        volumeManagerManager.instantiateVolumeManager(VolumeManagerManager.OutputType.REGULAR_IMAGE,
            windowWidth, windowHeight, scene)
    }

    override fun runAsynchronously() {
        while (renderer?.shouldClose == false) {
            renderer?.screenshot("naive_interface_output.png")

            //sleep for 10 seconds
            Thread.sleep(10000)
        }
    }
}