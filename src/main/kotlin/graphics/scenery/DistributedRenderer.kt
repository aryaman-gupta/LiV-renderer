package graphics.scenery

import graphics.scenery.textures.Texture
import graphics.scenery.utils.extensions.fetchFromGPU
import graphics.scenery.utils.lazyLogger
import java.nio.ByteBuffer

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

    open val firstPassFlag = ""
    open val secondPassFlag = ""

    var firstPass = true
    var secondPass = false

    open fun getFirstPassData(): ByteBuffer {
        val firstPassTexture = volumeManagerManager.getFirstPassTextureOrNull()!!
        val textureFetched = firstPassTexture.fetchFromGPU()

        if (!textureFetched) {
            throw RuntimeException("Error fetching first pass texture.").also { it.printStackTrace() }
        }

        return firstPassTexture.contents!!
    }

    abstract fun processFirstPassData(data: ByteBuffer)

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

    fun postRender() {
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
        }
    }
}