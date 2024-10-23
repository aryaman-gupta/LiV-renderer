package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.RichNode
import graphics.scenery.VDICompositorNode
import graphics.scenery.VolumeManagerManager
import graphics.scenery.textures.Texture
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.system.measureNanoTime
import kotlin.math.ceil

class DistributedVDIs(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters)
    : DistributedRenderer(volumeManagerManager, mpiParameters) {

    override val twoPassRendering = true

    override val firstPassFlag = "doThreshSearch"
    override val secondPassFlag = "doGeneration"

    private var prefixBuffer: ByteBuffer? = null
    private var totalSupersegmentsGenerated = 0

    val windowWidth = volumeManagerManager.getVDIVolumeManager().getVDIWidth()
    val windowHeight = volumeManagerManager.getVDIVolumeManager().getVDIHeight()
    val numSupersegments = volumeManagerManager.getVDIVolumeManager().getMaxSupersegments()

    var distributeColorPointer: Long = 0L
    var distributeDepthPointer: Long = 0L
    var distributePrefixPointer: Long = 0L
    var mpiPointer: Long = 0L

    @Suppress("unused")
    private external fun distributeVDIs(subVDIColor: ByteBuffer, subVDIDepth: ByteBuffer, prefixSums: ByteBuffer, supersegmentCounts: IntArray, commSize: Int,
                                        colPointer: Long, depthPointer: Long, prefixPointer: Long, mpiPointer: Long)

    override fun setupCompositor(): VDICompositorNode {
        return VDICompositorNode(windowWidth, windowHeight, numSupersegments, mpiParameters.commSize)
    }

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

        distributeVDIs(colorBuffer, depthBuffer, prefixBuffer!!, supersegmentCounts, commSize, distributeColorPointer,
            distributeDepthPointer, distributePrefixPointer, mpiPointer)
    }

    override fun uploadForCompositing(cam: Camera, buffersToUpload: List<ByteBuffer>, camera: Camera, colorCounts: IntArray, depthCounts: IntArray) {
        // Upload data for compositing
        val compositor = compositorNode as VDICompositorNode

        if (buffersToUpload.size != 3) {
            Exception("Expected 3 buffers, got ${buffersToUpload.size}").printStackTrace()
            return
        }
        val vdiSetColour = buffersToUpload[0]
        val vdiSetDepth = buffersToUpload[1]
        val prefixSet = buffersToUpload[2]

        val supersegmentsRecvd = (vdiSetColour.remaining() / (4*4)).toFloat() //including potential 0 supersegments that were padded

        logger.debug("Rank: ${mpiParameters.rank}: total supsegs recvd (including 0s): $supersegmentsRecvd")

        for (i in 0 until mpiParameters.commSize) {
            compositor.totalSupersegmentsFrom[i] = colorCounts[i] / (4 * 4)
            logger.debug("Rank ${mpiParameters.rank}: totalSupersegmentsFrom $i: ${colorCounts[i] / (4 * 4)}")
        }

        compositor.material().textures["VDIsColor"] = Texture(Vector3i(512, 512, ceil((supersegmentsRecvd / (512*512)).toDouble()).toInt()), 4, contents = vdiSetColour, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        compositor.material().textures["VDIsDepth"] = Texture(Vector3i(2 * 512, 512, ceil((supersegmentsRecvd / (512*512)).toDouble()).toInt()), 1, contents = vdiSetDepth, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        compositor.material().textures["VDIsPrefix"] = Texture(Vector3i(windowHeight, windowWidth, 1), 1, contents = prefixSet, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = IntType(), mipmap = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour
        )

        val view = cam.spatial().getTransformation()
        compositor.ViewOriginal = view
        compositor.invViewOriginal = Matrix4f(view).invert()

    }

    override fun setCompositorActivityStatus(setTo: Boolean) {
        val compositor = compositorNode as VDICompositorNode
        compositor.doComposite = setTo
    }
}