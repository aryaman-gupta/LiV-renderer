package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.Scene
import graphics.scenery.VDICompositorNode
import graphics.scenery.VolumeManagerManager
import graphics.scenery.natives.VDIMPIWrapper
import graphics.scenery.textures.Texture
import graphics.scenery.utils.extensions.applyVulkanCoordinateSystem
import net.imglib2.type.numeric.integer.IntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.system.measureNanoTime
import kotlin.math.ceil

class DistributedVDIsParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, scene: Scene)
    : ParallelizationBase(volumeManagerManager, mpiParameters, scene) {

    override val twoPassRendering = true

    override val firstPassFlag = "doThreshSearch"
    override val secondPassFlag = "doGeneration"

    override val explicitCompositingStep = true

    private var prefixBuffer: ByteBuffer? = null
    private var totalSupersegmentsGenerated = 0

    override var windowWidth = volumeManagerManager.getVDIVolumeManager().getVDIWidth()
    override var windowHeight = volumeManagerManager.getVDIVolumeManager().getVDIHeight()
    val numSupersegments = volumeManagerManager.getVDIVolumeManager().getMaxSupersegments()

    var distributeColorPointer: Long = 0L
    var distributeDepthPointer: Long = 0L
    var distributePrefixPointer: Long = 0L
    var mpiPointer: Long = 0L

    override val compositedColorsTextureName: String = "CompositedVDIColor"
    override val compositedDepthsTextureName: String = "CompositedVDIDepth"

    val nativeHandle = VDIMPIWrapper.initializeVDIResources(volumeManagerManager.getVDIVolumeManager().maxColorBufferSize,
        volumeManagerManager.getVDIVolumeManager().maxDepthBufferSize,
        volumeManagerManager.getVDIVolumeManager().prefixBufferSize,
        volumeManagerManager.getVDIVolumeManager().uncompressedColorBufferSize,
        volumeManagerManager.getVDIVolumeManager().uncompressedDepthBufferSize)

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

        // Calculate supersegment counts
        val supersegmentCounts = IntArray(commSize)
        val supersegmentCountsRecv = IntArray(commSize)

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

        // First distribute supersegmentCounts via MPI
        val distributedSupersegmentCounts = VDIMPIWrapper.distributeSupersegmentCounts(nativeHandle, supersegmentCounts, commSize)

        // Copy the received counts to our local array
        for (i in 0 until commSize) {
            supersegmentCountsRecv[i] = distributedSupersegmentCounts[i]
        }

        // Calculate color counts and displacements
        val colorCounts = IntArray(commSize)
        val colorDisplacements = IntArray(commSize)
        var colorDisplacementSum = 0

        for (i in 0 until commSize) {
            colorCounts[i] = supersegmentCounts[i] * 4 * 4
            colorDisplacements[i] = colorDisplacementSum
            colorDisplacementSum += colorCounts[i]
        }

        // Calculate depth counts and displacements
        val depthCounts = IntArray(commSize)
        val depthDisplacements = IntArray(commSize)
        var depthDisplacementSum = 0

        for (i in 0 until commSize) {
            depthCounts[i] = supersegmentCounts[i] * 4 * 2
            depthDisplacements[i] = depthDisplacementSum
            depthDisplacementSum += depthCounts[i]
        }

        // Calculate receive counts and displacements
        val colorCountsRecv = IntArray(commSize)
        val colorDisplacementsRecv = IntArray(commSize)
        var colorDisplacementRecvSum = 0

        val depthCountsRecv = IntArray(commSize)
        val depthDisplacementsRecv = IntArray(commSize)
        var depthDisplacementRecvSum = 0

        for (i in 0 until commSize) {
            colorCountsRecv[i] = supersegmentCountsRecv[i] * 4 * 4
            colorDisplacementsRecv[i] = colorDisplacementRecvSum
            colorDisplacementRecvSum += colorCountsRecv[i]

            depthCountsRecv[i] = supersegmentCountsRecv[i] * 4 * 2
            depthDisplacementsRecv[i] = depthDisplacementRecvSum
            depthDisplacementRecvSum += depthCountsRecv[i]
        }

        // Now perform the MPI_Alltoallv operations for color and depth
        val distributedColors = VDIMPIWrapper.distributeColorVDI(
            nativeHandle,
            colorBuffer,
            colorCounts,
            colorDisplacements,
            colorCountsRecv,
            colorDisplacementsRecv,
            commSize
        )

        val distributedDepths = VDIMPIWrapper.distributeDepthVDI(
            nativeHandle,
            depthBuffer,
            depthCounts,
            depthDisplacements,
            depthCountsRecv,
            depthDisplacementsRecv,
            commSize
        )

        // Distribute prefix buffer
        val prefixSet = VDIMPIWrapper.distributePrefixVDI(nativeHandle, prefixBuffer!!, mpiParameters.commSize)

        val distributedBuffers = listOf(distributedColors, distributedDepths, prefixSet)

        val camera = scene.findObserver()
        if (camera == null) {
            IllegalStateException("Camera not found in scene")
        }

        uploadForCompositing(distributedBuffers, camera as Camera, supersegmentCountsRecv.map { it * 4 * 4 }.toIntArray())
    }

    override fun uploadForCompositing(buffersToUpload: List<ByteBuffer>, camera: Camera, elementCounts: IntArray) {
        // Upload data for compositing
        val compositor = compositorNode as VDICompositorNode

//        compositor.nw = volumeManagerManager.getVolumeManager().shaderProperties.get("nw") as Float
        compositor.nw = 0.1f

        compositor.ProjectionOriginal = Matrix4f(camera.spatial().projection).applyVulkanCoordinateSystem()
        compositor.invProjectionOriginal = Matrix4f(camera.spatial().projection).applyVulkanCoordinateSystem().invert()

        compositor.numProcesses = mpiParameters.commSize
        compositor.vdiWidth = windowWidth
        compositor.vdiHeight = windowHeight
        compositor.isCompact = true

        compositor.ViewOriginal = camera.spatial().getTransformation()
        compositor.invViewOriginal = Matrix4f(camera.spatial().getTransformation()).invert()

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
            compositor.totalSupersegmentsFrom[i] = elementCounts[i] / (4 * 4)
            logger.debug("Rank ${mpiParameters.rank}: totalSupersegmentsFrom $i: ${elementCounts[i] / (4 * 4)}")
        }

        compositor.material().textures[compositedColorsTextureName] = Texture(Vector3i(512, 512, ceil((supersegmentsRecvd / (512*512)).toDouble()).toInt()), 4, contents = vdiSetColour, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        compositor.material().textures[compositedDepthsTextureName] = Texture(Vector3i(2 * 512, 512, ceil((supersegmentsRecvd / (512*512)).toDouble()).toInt()), 1, contents = vdiSetDepth, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

        compositor.material().textures["VDIsPrefix"] = Texture(Vector3i(windowHeight, windowWidth, 1), 1, contents = prefixSet, usageType = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = IntType(), mipmap = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour
        )

        val view = camera.spatial().getTransformation()
        compositor.ViewOriginal = view
        compositor.invViewOriginal = Matrix4f(view).invert()

    }

    override fun gatherCompositedOutput(buffers: List<ByteBuffer>) {
        val colorBuffer = buffers[0]
        val depthBuffer = buffers[1]
        val compositedVDILen = colorBuffer.remaining() / (4 * 4)

        if(colorBuffer.remaining() != volumeManagerManager.getVDIVolumeManager().uncompressedColorBufferSize/mpiParameters.commSize) {
            logger.error("Color buffer size mismatch. Expected ${volumeManagerManager.getVDIVolumeManager().uncompressedColorBufferSize/mpiParameters.commSize}, got ${colorBuffer.remaining()}")
        }

        if(depthBuffer.remaining() != volumeManagerManager.getVDIVolumeManager().uncompressedDepthBufferSize/mpiParameters.commSize) {
            logger.error("Depth buffer size mismatch. Expected ${volumeManagerManager.getVDIVolumeManager().uncompressedDepthBufferSize/mpiParameters.commSize}, got ${depthBuffer.remaining()}")
        }

        val gatheredColors = VDIMPIWrapper.gatherColorVDI(nativeHandle, colorBuffer, colorBuffer.remaining(), rootRank, colorBuffer.remaining() * mpiParameters.commSize)
        val gatheredDepths = VDIMPIWrapper.gatherDepthVDI(nativeHandle, depthBuffer, depthBuffer.remaining(), rootRank, depthBuffer.remaining() * mpiParameters.commSize)

        if (isRootProcess()) {
            // put the composited colors into the final composited buffer list
            gatheredColors?.let {
                finalBuffers.add(it)
            }
            gatheredDepths?.let {
                finalBuffers.add(it)
            }
        }
    }

    override fun setCompositorActivityStatus(setTo: Boolean) {
        val compositor = compositorNode as VDICompositorNode
        compositor.doComposite = setTo
    }

    override fun streamOutput() {
        TODO("Not yet implemented")
    }
}
