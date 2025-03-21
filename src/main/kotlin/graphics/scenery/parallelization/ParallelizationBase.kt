package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.Mesh
import graphics.scenery.Node
import graphics.scenery.RichNode
import graphics.scenery.VolumeManagerManager
import graphics.scenery.backends.Renderer
import graphics.scenery.natives.MPIJavaWrapper
import graphics.scenery.textures.Texture
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.utils.extensions.fetchFromGPU
import graphics.scenery.utils.lazyLogger
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.exitProcess

/**
 * Data class representing MPI (Message Passing Interface) parameters.
 *
 * @property rank The rank of the current process.
 * @property commSize The total number of processes in the communicator.
 * @property nodeRank The rank of the process within its node.
 */
data class MPIParameters(
    val rank: Int,
    val commSize: Int,
    val nodeRank: Int
)

/**
 * Abstract class defining the basic parallel execution structure for distributed rendering.
 *
 * @property volumeManagerManager The manager responsible for handling volume data.
 * @property mpiParameters The MPI parameters for the current process.
 */
abstract class ParallelizationBase(var volumeManagerManager: VolumeManagerManager, val mpiParameters: MPIParameters, val camera: Camera) {

    val logger by lazyLogger()

    open val twoPassRendering = false
    open val explicitCompositingStep = false

    open val firstPassFlag = ""
    open val secondPassFlag = ""

    var firstPass = true
    var secondPass = false
    var compositingPass = false
    var finalOutputReady = false

    protected var videoStreamRunning = false

    var compositorNode: RichNode? = null

    protected open var windowWidth: Int = 0
    protected open var windowHeight: Int = 0

    var streamGeneratedData = false
    var saveGeneratedData = false
    private var displayGeneratedData = false
    var displayObject: Mesh? = null

    private var frameNumber = 0

    private var previousCameraPosition = Vector3f(0f, 0f, 0f)
    private var previousCameraRotation = Quaternionf()

    fun setDisplayGeneratedData(displayObject: Mesh) {
        displayGeneratedData = true
        this.displayObject = displayObject
    }

    /**
     * The final composited buffers generated by the parallel rendering strategy.
     *
     * The buffers must be in the following order:
     * 1. Color buffer
     * 2. Depth buffer (if any)
     * 3. Additional buffers, e.g., alpha (if any)
     */
    protected val finalCompositedBuffers: MutableList<ByteBuffer> = mutableListOf()

    private val rootRank = 0

    protected fun isRootProcess(): Boolean {
        return mpiParameters.rank == rootRank
    }

    /**
     * Sets up the compositor node. Only called if the derived class sets [explicitCompositingStep] to true.
     *
     * @return The compositor node.
     */
    open fun setupCompositor() : RichNode? {
        return null
    }

    init {
        compositorNode = setupCompositor()

        volumeManagerManager.getVolumeManager().hub?.let {
            it.get<Renderer>()?.let { renderer ->
                windowWidth = renderer.window.width
                windowHeight = renderer.window.height
            } ?: run {
                throw RuntimeException("Please ensure that the ParallelizationBase class is initialized after the Renderer, with" +
                        "a valid and initialized VolumeManagerManager")
            }
        } ?: run {
            throw RuntimeException("Please ensure that the ParallelizationBase class is initialized after the Renderer, with" +
                    "a valid and initialized VolumeManagerManager")
        }
    }

    /**
     * Fetches the data generated in the first rendering pass in the form of a texture. Only called if [twoPassRendering] is true.
     *
     * @return The data from the first pass texture.
     * @throws RuntimeException if there is an error fetching the texture.
     */
    open fun getFirstPassData(): ByteBuffer {
        val firstPassTexture = volumeManagerManager.getFirstPassTextureOrNull()!!
        val textureFetched = firstPassTexture.fetchFromGPU()

        if (!textureFetched) {
            throw RuntimeException("Error fetching first pass texture.").also { it.printStackTrace() }
        }

        return firstPassTexture.contents!!
    }

    /**
     * Override to process first pass data if the renderer is using a 2-pass approach.
     *
     * @param data The data from the first pass.
     */
    open fun processFirstPassData(data: ByteBuffer) {

    }

    /**
     * Override to fetch additional textures (beyond color and depth) generated by the parallel rendering strategy before the
     * compositing stage.
     *
     * @return A list of additional texture data.
     */
    open fun fetchAdditionalTextureData(): List<ByteBuffer> {
        return emptyList()
    }

    /**
     * Override to insert any preprocessing required before rendered buffers are distributed for compositing.
     *
     * @param process The process to execute.
     */
    fun preProcessBeforeDistribute(process: () -> Unit) {
        try {
            process()
        } catch (e: Exception) {
            logger.error("Error in pre-processing before distribution: ${e.message}")
        }
    }

    /**
     * Distributes the buffers generated in the rendering process for compositing. This function is responsible for
     * invoking [uploadForCompositing] after distributing the buffers.
     *
     * @param buffers The buffers to distribute.
     */
    abstract fun distributeForCompositing(buffers: List<ByteBuffer>)

    /**
     * Uploads the data necessary for compositing. This function should be overridden to upload data and update necessary
     * camera parameters for compositing.
     *
     * @param buffersToUpload The buffers to upload.
     * @param camera The camera to update.
     * @param colorCounts If run-length encoding is used, the process-wise start points of the color data can be passed into this array. The size of array should be equal to the number of processes.
     * @param depthCounts If run-length encoding is used, the process-wise start points of the depth data can be passed into this array. The size of array should be equal to the number of processes.
     */
    open fun uploadForCompositing(buffersToUpload: List<ByteBuffer>, camera: Camera, colorCounts: IntArray, depthCounts: IntArray) {
        // Override to upload data and update necessary camera parameters for compositing
    }

    /**
     * Set the compositor activity status. If the parallel execution strategy contains an explicit compositing pass, this
     * function should be overridden to activate or deactivate the compositing shader based on the value of [setTo].
     *
     * @param setTo Boolean value to set the compositor activity status to.
     */
    open fun setCompositorActivityStatus(setTo: Boolean) {

    }

    abstract fun streamOutput()

    /**
     * The main function controlling the parallel rendering execution. This function is called after the rendering process
     * is complete and is responsible for fetching the rendered buffers, distributing them for compositing, and orchestrating
     * the renderer to execute the rendering and compositing processes in the correct order.
     */
    fun postRender() {

        if(!twoPassRendering) {
            val buffersToDistribute: MutableList<ByteBuffer> = mutableListOf()
            val colorTexture = volumeManagerManager.getColorTextureOrNull()!!
            var textureFetched = colorTexture.fetchFromGPU()
            if (!textureFetched) {
                throw RuntimeException("Error fetching color texture.").also { it.printStackTrace() }
            }

            buffersToDistribute.add(colorTexture.contents!!)

            // can't assume that a depth texture will always be present
            val depthTexture = volumeManagerManager.getDepthTextureOrNull()
            if(depthTexture != null) {
                textureFetched = depthTexture.fetchFromGPU()
                if (!textureFetched) {
                    throw RuntimeException("Error fetching depth texture.").also { it.printStackTrace() }
                }

                buffersToDistribute.add(depthTexture.contents!!)
            }

            fetchAdditionalTextureData().forEach {
                buffersToDistribute.add(it)
            }

            distributeForCompositing(buffersToDistribute)

            if(explicitCompositingStep) {
                compositingPass = true
                firstPass = false
                volumeManagerManager.getVolumeManager().shaderProperties[firstPassFlag] = false
            }

            //TODO: is this correct for cases where there is an explicit compositing step?
            finalOutputReady = true
        } else {
            if(firstPass) {
                // Data generated in the first pass is fetched and processed
                val firstPassData = getFirstPassData()
                processFirstPassData(firstPassData)

                firstPass = false
                secondPass = true

                volumeManagerManager.getVolumeManager().shaderProperties[firstPassFlag] = false
                volumeManagerManager.getVolumeManager().shaderProperties[secondPassFlag] = true
            } else if(secondPass) {
                // Final generated (rendered) buffers are fetched and distributed for compositing

                val buffersToDistribute: MutableList<ByteBuffer> = mutableListOf()
                val colorTexture = volumeManagerManager.getColorTextureOrNull()!!
                var textureFetched = colorTexture.fetchFromGPU()
                if (!textureFetched) {
                    throw RuntimeException("Error fetching color texture.").also { it.printStackTrace() }
                }

                buffersToDistribute.add(colorTexture.contents!!)

                // safe to assume that a 2-pass approach will always have a depth texture
                val depthTexture = volumeManagerManager.getDepthTextureOrNull()!!
                textureFetched = depthTexture.fetchFromGPU()
                if (!textureFetched) {
                    throw RuntimeException("Error fetching depth texture.").also { it.printStackTrace() }
                }

                buffersToDistribute.add(depthTexture.contents!!)

                fetchAdditionalTextureData().forEach {
                    buffersToDistribute.add(it)
                }

                distributeForCompositing(buffersToDistribute)
                // the distribute code will then call the [uploadForCompositing] function which will upload the data necessary for compositing

                if(explicitCompositingStep) {
                    secondPass = false
                    compositingPass = true
                    setCompositorActivityStatus(true)
                    volumeManagerManager.getVolumeManager().shaderProperties[secondPassFlag] = false
                } else {
                    secondPass = false
                    firstPass = true
                    finalOutputReady = true
                    volumeManagerManager.getVolumeManager().shaderProperties[firstPassFlag] = true
                }
            }
        }

        if(explicitCompositingStep && compositingPass) {
            // The compositing pass just completed
            compositingPass = false
            setCompositorActivityStatus(false)
            firstPass = true
            volumeManagerManager.getVolumeManager().shaderProperties[firstPassFlag] = true
        }

    }

    fun processCompositedOutput() {
        frameNumber++
        if((this is TestParallelization) || (isRootProcess() && finalOutputReady)) {
            finalOutputReady = false
            if(displayGeneratedData) {
                displayObject?.let {
                    val bufferLE = finalCompositedBuffers.first().order(ByteOrder.LITTLE_ENDIAN)
                    //TODO: fix memory leak on GPU caused my creating a new texture each time
                    displayObject!!.material().textures["diffuse"] =
                        Texture(Vector3i(windowWidth, windowHeight, 1), 4, contents = bufferLE, mipmap = true)

                }
            }

            if(saveGeneratedData) {
                finalCompositedBuffers.forEachIndexed { index, buffer ->
                    SystemHelpers.dumpToFile(buffer, "composited_output_frame_${frameNumber}_$index.raw")
                }
            }

            if(streamGeneratedData) {
                streamOutput()
            }
        }

        finalCompositedBuffers.clear()
    }

    fun synchronizeCamera() {
        val cameraData = ByteBuffer.allocate(7 * 4).order(ByteOrder.LITTLE_ENDIAN)
        cameraData.putFloat(camera.spatial().position.x)
        cameraData.putFloat(camera.spatial().position.y)
        cameraData.putFloat(camera.spatial().position.z)
        cameraData.putFloat(camera.spatial().rotation.x)
        cameraData.putFloat(camera.spatial().rotation.y)
        cameraData.putFloat(camera.spatial().rotation.z)
        cameraData.putFloat(camera.spatial().rotation.w)
        val cameraByteArray = cameraData.array()

        if(mpiParameters.rank != rootRank) {
            logger.debug("On rank: ${mpiParameters.rank}, before broadcast camera pose was: ${camera.spatial().position}, ${camera.spatial().rotation}")
        }

        MPIJavaWrapper.bcast(cameraByteArray, 0)
        // since the array was updated in-place, we have the changed camera position

        if(mpiParameters.rank != rootRank) {
            logger.debug("On rank: ${mpiParameters.rank}, after broadcast, camera pose is: ${camera.spatial().position}, ${camera.spatial().rotation}")
        }

        val newCameraData = ByteBuffer.wrap(cameraByteArray).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()

        camera.spatial().position = Vector3f(newCameraData[0], newCameraData[1], newCameraData[2])
        camera.spatial().rotation = Quaternionf(newCameraData[3], newCameraData[4], newCameraData[5], newCameraData[6])

        previousCameraPosition = camera.spatial().position
        previousCameraRotation = camera.spatial().rotation
    }
}