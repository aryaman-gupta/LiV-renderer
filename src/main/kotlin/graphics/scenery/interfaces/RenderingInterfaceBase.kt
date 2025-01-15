package graphics.scenery.interfaces

import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.Origin
import graphics.scenery.SceneryBase
import graphics.scenery.VolumeManagerManager
import graphics.scenery.backends.Renderer
import graphics.scenery.parallelization.ParallelizationBase
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess

abstract class RenderingInterfaceBase(applicationName: String, windowWidth: Int, windowHeight: Int) : SceneryBase(applicationName, windowWidth, windowHeight) {

    var volumes: HashMap<Int, BufferedVolume?> = java.util.HashMap()
    var volumeManagerInitialized = AtomicBoolean(false)
    var volumesCreated = AtomicBoolean(false)
    var sceneSetupComplete = AtomicBoolean(false)

    private var volumeDimensions: IntArray = intArrayOf(0, 0, 0)

    private var pixelToWorld = 0.001f

    lateinit var volumeManagerManager: VolumeManagerManager
    val volumeDimensionsInitialized = AtomicBoolean(false)

    abstract val parallelizationScheme: ParallelizationBase

    var commSize = 1
    var rank = 0
    var nodeRank = 0

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

    fun stopRendering() {
        renderer?.shouldClose = true
    }

    fun setVolumeDimensions(dims: IntArray) {
        if (dims.size != 3) {
            logger.error("Volume dimensions must be a 3-element array")
            exitProcess(1)
        }
        if (volumeDimensionsInitialized.get()) {
            logger.error("Volume dimensions can only be set once and must remain constant")
            exitProcess(1)
        }
        volumeDimensions = dims
        volumeDimensionsInitialized.set(true)

        pixelToWorld = 3.84f / volumeDimensions[2] // empirically found to work reasonably
    }

    fun getVolumeScaling(): Float {

        return pixelToWorld
    }

    open fun addVolume(volumeID: Int, dimensions: IntArray, pos: FloatArray, is16bit: Boolean) {
        logger.info("Trying to add the volume")
        logger.info("id: $volumeID, dims: ${dimensions[0]}, ${dimensions[1]}, ${dimensions[2]} pos: ${pos[0]}, ${pos[1]}, ${pos[2]}")

        if(!volumeDimensionsInitialized.get()) {
            logger.error("Please initialize the volume dimensions (using the setVolumeDimensions function) before adding volumes!")
            exitProcess(1)
        }

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

    abstract fun setupVolumeManagerManager()

    open fun additionalSceneSetup() {}

    open fun runAsynchronously() {}

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        setupVolumeManagerManager()

        volumeManagerInitialized.set(true)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial().position = Vector3f(-2.300E+0f, -6.402E+0f, 1.100E+0f)
            spatial().rotation = Quaternionf(2.495E-1, -7.098E-1, 3.027E-1, -5.851E-1)

            perspectiveCamera(50.0f, windowWidth, windowHeight)
            cam.farPlaneDistance = 20.0f
        }
        scene.addChild(cam)

        additionalSceneSetup()

        while (!sceneSetupComplete.get()) {
            Thread.sleep(50)
        }

        thread {
            while (renderer?.firstImageReady == false) {
                Thread.sleep(50)
            }

            runAsynchronously()
        }
    }
}