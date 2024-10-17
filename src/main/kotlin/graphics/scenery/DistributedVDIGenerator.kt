package graphics.scenery

import graphics.scenery.textures.Texture
import net.imglib2.type.numeric.integer.IntType
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.system.measureNanoTime

class DistributedVDIGenerator(volumeManagerManager: VolumeManagerManager, val windowWidth: Int, val windowHeight: Int, mpiParameters: MPIParameters)
    : DistributedRenderer(volumeManagerManager, mpiParameters) {

    override val twoPassRendering = true

    override val firstPassFlag = "doThreshSearch"
    override val secondPassFlag = "doGeneration"

    private var prefixBuffer: ByteBuffer? = null
    private var totalSupersegmentsGenerated = 0

    var distributeColorPointer: Long = 0L
    var distributeDepthPointer: Long = 0L
    var distributePrefixPointer: Long = 0L
    var mpiPointer: Long = 0L

    private external fun distributeVDIs(subVDIColor: ByteBuffer, subVDIDepth: ByteBuffer, prefixSums: ByteBuffer, supersegmentCounts: IntArray, commSize: Int,
                                        colPointer: Long, depthPointer: Long, prefixPointer: Long, mpiPointer: Long)

    override fun processFirstPassData(data: ByteBuffer) {
        // Process first pass data
        val numGeneratedIntBuffer = data.asIntBuffer()

        val prefixTime = measureNanoTime {
            prefixBuffer = MemoryUtil.memAlloc(windowWidth * windowHeight * 4)
            val prefixIntBuff = prefixBuffer!!.asIntBuffer()

            prefixIntBuff.put(0, 0)

            for(i in 1 until windowWidth * windowHeight) {
                prefixIntBuff.put(i, prefixIntBuff.get(i-1) + numGeneratedIntBuffer.get(i-1))
            }

            totalSupersegmentsGenerated = prefixIntBuff.get(windowWidth*windowHeight-1) + numGeneratedIntBuffer.get(windowWidth*windowHeight-1)
        }
        logger.debug("Prefix sum took ${prefixTime/1e9} to compute")


        volumeManagerManager.getVolumeManager().material().textures["PrefixSums"] = Texture(
            Vector3i(windowHeight, windowWidth, 1), 1, contents = prefixBuffer, usageType = hashSetOf(
                Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture)
            , type = IntType(),
            mipmap = false,
            minFilter = Texture.FilteringMode.NearestNeighbour,
            maxFilter = Texture.FilteringMode.NearestNeighbour
        )

    }

    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // Distribute buffers for compositing

        if(buffers.size != 2) {
            logger.error("Expected 2 buffers, got ${buffers.size}")
            return
        }

        val colorBuffer = buffers[0]
        val depthBuffer = buffers[1]

        val rank = mpiParameters.rank
        val commSize = mpiParameters.commSize
        val supersegmentCounts = IntArray(commSize)

        preProcessBeforeDistribute {
            val prefixIntBuff = prefixBuffer!!.asIntBuffer()

            val totalLists = windowHeight * windowWidth
            var supersegmentsSoFar = 0
            for(i in 0 until (commSize-1)) {
                supersegmentCounts[i] = prefixIntBuff.get((totalLists / commSize) * (i + 1)) - supersegmentsSoFar
                logger.debug("Rank: $rank will send ${supersegmentCounts[i]} supersegments to process $i")
                supersegmentsSoFar += supersegmentCounts[i];
            }
            supersegmentCounts[commSize-1] = totalSupersegmentsGenerated - supersegmentsSoFar
            logger.debug("Rank: $rank will send ${supersegmentCounts[commSize-1]} supersegments to process ${commSize-1}")
        }

        distributeVDIs(colorBuffer, depthBuffer, prefixBuffer!!, supersegmentCounts, commSize, distributeColorPointer, distributeDepthPointer, distributePrefixPointer, mpiPointer)
    }
}