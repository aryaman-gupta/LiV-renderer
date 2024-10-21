package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.RichNode
import graphics.scenery.VolumeManagerManager
import graphics.scenery.utils.extensions.fetchFromGPU
import graphics.scenery.utils.lazyLogger
import java.nio.ByteBuffer
import kotlin.math.exp

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

abstract class DistributedRenderer(var volumeManagerManager: VolumeManagerManager, val mpiParameters: MPIParameters) {

    val logger by lazyLogger()

    open val twoPassRendering = false
    open val explicitCompositingStep = false

    open val firstPassFlag = ""
    open val secondPassFlag = ""

    var firstPass = true
    var secondPass = false
    var compositingPass = false

    var compositorNode: RichNode? = null

    open fun setupCompositor() : RichNode? {
        return null
    }

    init {
        compositorNode = setupCompositor()
    }

    open fun getFirstPassData(): ByteBuffer {
        val firstPassTexture = volumeManagerManager.getFirstPassTextureOrNull()!!
        val textureFetched = firstPassTexture.fetchFromGPU()

        if (!textureFetched) {
            throw RuntimeException("Error fetching first pass texture.").also { it.printStackTrace() }
        }

        return firstPassTexture.contents!!
    }

    open fun processFirstPassData(data: ByteBuffer) {
        // Override to process first pass data if the renderer is using a 2-pass approach
    }

    open fun fetchAdditionalTextureData(): List<ByteBuffer> {
        return emptyList()
    }

    fun preProcessBeforeDistribute(process: () -> Unit) {
        try {
            process()
        } catch (e: Exception) {
            logger.error("Error in pre-processing before distribution: ${e.message}")
        }
    }

    abstract fun distributeForCompositing(buffers: List<ByteBuffer>)

    open fun uploadForCompositing(buffersToUpload: List<ByteBuffer>, camera: Camera) {
        // Override to upload data and update necessary camera parameters for compositing
    }

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
                    volumeManagerManager.getVolumeManager().shaderProperties[secondPassFlag] = false
                } else {
                    secondPass = false
                    firstPass = true
                    volumeManagerManager.getVolumeManager().shaderProperties[firstPassFlag] = true
                }
            }
        }

        if(explicitCompositingStep && compositingPass) {
            // The compositing pass just completed
            compositingPass = false
            firstPass = true
            volumeManagerManager.getVolumeManager().shaderProperties[firstPassFlag] = true
        }

    }
}