package graphics.scenery.interfaces

import graphics.scenery.Origin
import graphics.scenery.SceneryBase
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

abstract class RenderingInterfaceBase(applicationName: String, windowWidth: Int, windowHeight: Int) : SceneryBase(applicationName, windowWidth, windowHeight) {

    var volumes: HashMap<Int, BufferedVolume?> = java.util.HashMap()
    var volumeManagerInitialized = AtomicBoolean(false)
    var volumesCreated = AtomicBoolean(false)

    open var pixelToWorld = 0.001f

    /**
     * Blocks until the renderer is instantiated and initialized.
     */
    open fun waitRendererReady() {
        while (renderer == null) {
            Thread.sleep(50)
        }
        while (!renderer!!.initialized) {
            Thread.sleep(50)
        }
    }

    open fun addVolume(volumeID: Int, dimensions: IntArray, pos: FloatArray, is16bit: Boolean) {
        logger.info("Trying to add the volume")
        logger.info("id: $volumeID, dims: ${dimensions[0]}, ${dimensions[1]}, ${dimensions[2]} pos: ${pos[0]}, ${pos[1]}, ${pos[2]}")

        while (!volumeManagerInitialized.get()) {
            Thread.sleep(50)
        }

        val volume = if (is16bit) {
            Volume.fromBuffer(emptyList(), dimensions[0], dimensions[1], dimensions[2], UnsignedShortType(), hub)
        } else {
            Volume.fromBuffer(emptyList(), dimensions[0], dimensions[1], dimensions[2], UnsignedByteType(), hub)
        }
        volume.origin = Origin.FrontBottomLeft
        volume.spatial().position = Vector3f(pos[0], pos[1], pos[2])

        volume.spatial().needsUpdate = true
        volume.colormap = Colormap.get("rb-darker")
        volume.pixelToWorldRatio = pixelToWorld

        val tf = TransferFunction()

        with(tf) {
            addControlPoint(0.18f, 0.28f)
            addControlPoint(0.6f, 0.3f)
            addControlPoint(0.88f, 0.4f)
            addControlPoint(1f, 0.5f)
        }

        volume.name = "volume"

        volume.transferFunction = tf

        scene.addChild(volume)

        volumes[volumeID] = volume
        volumesCreated.set(true)
    }

    open fun updateVolume(volumeId: Int, buffer: ByteBuffer) {
        while (volumes[volumeId] == null) {
            logger.info("Waiting for volume $volumeId to be created")
            Thread.sleep(50)
        }
        logger.info("Volume $volumeId has been updated")
        volumes[volumeId]?.addTimepoint("t", buffer)
        volumes[volumeId]?.goToLastTimepoint()
    }
}