package graphics.scenery.interfaces

import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.FullscreenObject
import graphics.scenery.Origin
import graphics.scenery.SceneryBase
import graphics.scenery.Settings
import graphics.scenery.VolumeManagerManager
import graphics.scenery.backends.Renderer
import graphics.scenery.parallelization.MPIParameters
import graphics.scenery.parallelization.ParallelizationBase
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess

abstract class RenderingInterfaceBase(applicationName: String, windowWidth: Int, windowHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : SceneryBase(
    "Rank: $rank:$applicationName", windowWidth, windowHeight) {

    var volumes: HashMap<Int, BufferedVolume?> = java.util.HashMap()
    var volumeManagerInitialized = AtomicBoolean(false)
    var volumesCreated = AtomicBoolean(false)
    var sceneSetupComplete = AtomicBoolean(false)

    protected var volumeDimensions: IntArray = intArrayOf(0, 0, 0)
    private set

    protected var pixelToWorld = 0.001f

    lateinit var volumeManagerManager: VolumeManagerManager
    val volumeDimensionsInitialized = AtomicBoolean(false)

    lateinit var parallelizationScheme: ParallelizationBase

    var plane: FullscreenObject? = null
    val mpiParameters = MPIParameters(rank, commSize, nodeRank)

    /**
     * Enum class representing the type of processing that will be applied to the final composited output.
     *
     * STREAM: The composited output will be streamed to a remote display.
     * SAVE_TO_DISK: The composited output will be saved to disk.
     * DISPLAY: The composited output will be displayed on the server machine.
     * STREAM_AND_SAVE: The composited output will be streamed to a remote display and saved to disk.
     */
    enum class OutputProcessingType {
        STREAM, SAVE_TO_DISK, DISPLAY, STREAM_AND_SAVE, NONE
    }

    abstract var outputProcessingType: OutputProcessingType

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

    /**
     * Sets the dimensions of the overall volume being rendered by the parallel rendering application, i.e.,
     * the combination of all domains that will be rendered by the individual processes. This function needs to be
     * called before adding any volumes to the scene.
     *
     * @param dims The dimensions of the overall volume as a 3-element array.
     */
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

        pixelToWorld = 3.84f / volumeDimensions[0] // empirically found to work reasonably
        volumeDimensionsInitialized.set(true)
    }

    fun getVolumeScaling(): Float {

        return pixelToWorld
    }

    private fun getVolumeSize(volumeID: Int): Int {
        if(!volumes.containsKey(volumeID)) {
            throw IllegalArgumentException("Volume with ID $volumeID does not exist")
            return 0
        } else {
            val dimensions = volumes[volumeID]!!.getDimensions()
            return dimensions.x * dimensions.y * dimensions.z
        }
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

        val tf = TransferFunction.ramp(0.0f, 1.0f, 1.0f)

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

        if(buffer.remaining() != getVolumeSize(volumeId)) {
            IllegalArgumentException("updateVolume called with a buffer of incorrect size for $volumeId. " +
                    "Expected ${getVolumeSize(volumeId)} bytes, got ${buffer.remaining()} bytes")
        }

        volumes[volumeId]?.addTimepoint("t", buffer)
        volumes[volumeId]?.goToLastTimepoint()
        logger.info("Volume $volumeId has been updated")
    }

    abstract fun setupVolumeManagerManager()

    abstract fun initializeParallelizationScheme(): ParallelizationBase

    open fun additionalSceneSetup() {}

    open fun runAsynchronously() {}

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        setupVolumeManagerManager()

        volumeManagerInitialized.set(true)

        val cam: Camera
        if(!Settings().get("RemoteCamera", false)) {
            cam = DetachedHeadCamera()
            with(cam) {
                spatial().position = Vector3f(-2.300E+0f, -6.402E+0f, 1.100E+0f)
                spatial().rotation = Quaternionf(2.495E-1, -7.098E-1, 3.027E-1, -5.851E-1)

                perspectiveCamera(50.0f, windowWidth, windowHeight)
                cam.farPlaneDistance = 20.0f
            }
            scene.addChild(cam)
        }

        while (!volumeDimensionsInitialized.get()) {
            Thread.sleep(50)
        }

        parallelizationScheme = initializeParallelizationScheme()

        if (outputProcessingType == OutputProcessingType.DISPLAY) {
            plane = FullscreenObject()
            scene.addChild(plane!!)
            parallelizationScheme.setDisplayGeneratedData(plane!!)
        } else if (outputProcessingType == OutputProcessingType.SAVE_TO_DISK) {
            parallelizationScheme.saveGeneratedData = true
        } else if (outputProcessingType == OutputProcessingType.STREAM) {
            parallelizationScheme.streamGeneratedData = true
        } else if (outputProcessingType == OutputProcessingType.STREAM_AND_SAVE) {
            parallelizationScheme.streamGeneratedData = true
            parallelizationScheme.saveGeneratedData = true
        }

        additionalSceneSetup()

        while (!sceneSetupComplete.get()) {
            Thread.sleep(50)
        }

        var frameNumber = 0

        renderer!!.runAfterRendering.add { if(frameNumber > 0) { parallelizationScheme.postRender() } }
        renderer!!.runAfterRendering.add { if(frameNumber > 0) { parallelizationScheme.processCompositedOutput() } }
        renderer!!.runAfterRendering.add {
            parallelizationScheme.synchronizeCamera()

            if(frameNumber > 0 && !Settings().get("LiV-Test-Benchmark", false)) {
                parallelizationScheme.synchronizeTransferFunction(volumes)
            }

            frameNumber++
        }

        thread {
            while (renderer?.firstImageReady == false) {
                Thread.sleep(50)
            }

            runAsynchronously()
        }
    }
}