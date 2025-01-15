package graphics.scenery.tests.interfaces

import graphics.scenery.FullscreenObject
import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase

class NaiveInterface(wWidth: Int, wHeight: Int) : RenderingInterfaceBase("NaiveInterface", wWidth, wHeight) {

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