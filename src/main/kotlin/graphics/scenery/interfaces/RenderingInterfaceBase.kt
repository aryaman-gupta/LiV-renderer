package graphics.scenery.interfaces

import graphics.scenery.Camera
import graphics.scenery.DetachedHeadCamera
import graphics.scenery.FullscreenObject
import graphics.scenery.Origin
import graphics.scenery.SceneryBase
import graphics.scenery.VolumeManagerManager
import graphics.scenery.backends.Renderer
import graphics.scenery.benchmarks.BenchmarkSetup
import graphics.scenery.benchmarks.BenchmarkSetup.Dataset
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

abstract class RenderingInterfaceBase(applicationName: String, windowWidth: Int, windowHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : SceneryBase(applicationName, windowWidth, windowHeight) {

    var volumes: HashMap<Int, BufferedVolume?> = java.util.HashMap()
    var volumeManagerInitialized = AtomicBoolean(false)
    var volumesCreated = AtomicBoolean(false)
    var sceneSetupComplete = AtomicBoolean(false)

    private var volumeDimensions: IntArray = intArrayOf(0, 0, 0)

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

        pixelToWorld = 3.84f / volumeDimensions[0] // empirically found to work reasonably
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

        val benchmarkDataset = System.getProperty("liv-renderer.BenchmarkDataset")

        if(benchmarkDataset != null) {
            logger.info("Benchmark Dataset: $benchmarkDataset")
        }

        val volume = if (is16bit) {
            Volume.fromBuffer(emptyList(), dimensions[0], dimensions[1], dimensions[2], UnsignedShortType(), hub)
        } else {
            Volume.fromBuffer(emptyList(), dimensions[0], dimensions[1], dimensions[2], UnsignedByteType(), hub)
        }
        volume.origin = Origin.FrontBottomLeft
        volume.spatial().position = Vector3f(pos[0], pos[1], pos[2])

        volume.spatial().needsUpdate = true
        if(benchmarkDataset != null) {
            BenchmarkSetup(Dataset.valueOf(benchmarkDataset)).setColorMap(volume)
        } else {
            volume.colormap = Colormap.get("rb-darker")
        }
        volume.pixelToWorldRatio = pixelToWorld

        if(benchmarkDataset != null) {
            volume.transferFunction = BenchmarkSetup(Dataset.valueOf(benchmarkDataset)).setupTransferFunction()
        } else {
            val tf = TransferFunction.ramp(0.0f, 1.0f, 1.0f)
            volume.transferFunction = tf
        }

        volume.name = "volume"

        scene.addChild(volume)

        volumes[volumeID] = volume
        volumesCreated.set(true)
    }

    open fun updateVolume(volumeId: Int, buffer: ByteBuffer) {
        while (volumes[volumeId] == null) {
            logger.info("Waiting for volume $volumeId to be created")
            Thread.sleep(50)
        }

        if(buffer.remaining() == 0) {
            IllegalArgumentException("updateVolume called with empty buffer for volume $volumeId")
        }

        volumes[volumeId]?.addTimepoint("t", buffer)
        volumes[volumeId]?.goToLastTimepoint()
        logger.info("Volume $volumeId has been updated")
    }

    abstract fun setupVolumeManagerManager()

    abstract fun initializeParallelizationScheme(camera: Camera): ParallelizationBase

    open fun additionalSceneSetup() {}

    open fun runAsynchronously() {}

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        setupVolumeManagerManager()

        volumeManagerInitialized.set(true)

        val cam: Camera = DetachedHeadCamera()

        val benchmarkDataset = System.getProperty("liv-renderer.BenchmarkDataset")

        if(benchmarkDataset != null) {
            BenchmarkSetup(Dataset.valueOf(benchmarkDataset)).positionCamera(cam)
        } else {
            with(cam) {
                spatial().position = Vector3f(-2.300E+0f, -6.402E+0f, 1.100E+0f)
                spatial().rotation = Quaternionf(2.495E-1, -7.098E-1, 3.027E-1, -5.851E-1)
            }
        }

        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        cam.farPlaneDistance = 20.0f

        scene.addChild(cam)

        parallelizationScheme = initializeParallelizationScheme(cam)

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

        renderer!!.runAfterRendering.add { parallelizationScheme.postRender() }
        renderer!!.runAfterRendering.add { parallelizationScheme.processCompositedOutput() }

        thread {
            while (renderer?.firstImageReady == false) {
                Thread.sleep(50)
            }

            runAsynchronously()
        }
    }
}