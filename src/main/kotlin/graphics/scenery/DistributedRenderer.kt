package graphics.scenery

import graphics.scenery.utils.extensions.fetchFromGPU
import graphics.scenery.utils.lazyLogger
import java.nio.ByteBuffer

abstract class DistributedRenderer(var volumeManagerManager: VolumeManagerManager) {

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
            logger.error("Error fetching first pass texture.")
        }

        return firstPassTexture.contents!!
    }

    abstract fun processFirstPassData(data: ByteBuffer)

    open fun fetchAdditionalTextureData(): List<ByteBuffer> {
        return emptyList()
    }

    open fun preProcess(process: () -> Unit) {
        process()
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
            buffersToDistribute.add(colorTexture.contents!!)

            // safe to assume that a 2-pass approach will always have a depth texture
            val depthTexture = volumeManagerManager.getDepthTextureOrNull()!!
            buffersToDistribute.add(depthTexture.contents!!)

            fetchAdditionalTextureData().forEach {
                buffersToDistribute.add(it)
            }
        }
    }
}