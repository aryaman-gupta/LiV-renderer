package graphics.scenery

import bvv.core.shadergen.generate.SegmentTemplate
import bvv.core.shadergen.generate.SegmentType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.VolumeManager
import graphics.scenery.volumes.vdi.VDIVolumeManager
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.FloatType
import org.lwjgl.system.MemoryUtil

class VolumeManagerManager (var hub: Hub) {

    val NUM_LAYERS = 2

    private lateinit var volumeManager: VolumeManager
    private var vdiVolumeManager: VDIVolumeManager? = null
    private var firstPassTexture: Texture? = null
    private var colorTexture: Texture? = null
    private var depthTexture: Texture? = null

    fun getFirstPassTextureOrNull(): Texture? {
        return firstPassTexture
    }

    fun getColorTextureOrNull(): Texture? {
        return colorTexture
    }

    fun getDepthTextureOrNull(): Texture? {
        return depthTexture
    }

    fun getVolumeManager(): VolumeManager {
        if (::volumeManager.isInitialized) {
        return volumeManager
        } else {
            throw UninitializedPropertyAccessException("VolumeManager has not been instantiated")
        }
    }

    fun getVDIVolumeManager(): VDIVolumeManager {
        if (vdiVolumeManager != null) {
            return vdiVolumeManager!!
        } else {
            throw UninitializedPropertyAccessException("VDIVolumeManager not found. " +
                    "VolumeManager needs to be instantiated with VDI output type.")
        }
    }

    enum class OutputType {
        REGULAR_IMAGE,
        LAYERED_IMAGE,
        VDI,
        TEST_VDI_FULL
    }

    private lateinit var outputType: OutputType

    fun getOutputType(): OutputType {
        if (::outputType.isInitialized) {
            return outputType
        } else {
            throw UninitializedPropertyAccessException("VolumeManager has not been instantiated")
        }
    }


    fun getColorTextureType(outputType: OutputType): Any {
        return UnsignedByteType()
    }

    fun getDepthTextureType(outputType: OutputType): Any {
        return if(outputType == OutputType.LAYERED_IMAGE) {
            FloatType()
        } else if(outputType == OutputType.VDI) {
            UnsignedShortType()
        } else {
            throw IllegalArgumentException("Depth texture type not supported for output type $outputType")
        }
    }

    private fun createVolumeManager(raycastShaderName: String, accumulateShader: String = ""): VolumeManager {
        if(accumulateShader == "") {
            return VolumeManager(
                hub,
                useCompute = true,
                customSegments = hashMapOf(
                    SegmentType.FragmentShader to SegmentTemplate(
                        this.javaClass,
                        raycastShaderName,
                        "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate"
                    ),
                )
            )
        } else {
            return VolumeManager(
                hub, useCompute = true,
                customSegments = hashMapOf(
                    SegmentType.FragmentShader to SegmentTemplate(
                        this::class.java,
                        raycastShaderName,
                        "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate",
                    ),
                    SegmentType.Accumulator to SegmentTemplate(
                        accumulateShader,
                        "vis", "localNear", "localFar", "sampleVolume", "convert", "sceneGraphVisibility"
                    ),
                ),
            )
        }
    }

    fun instantiateVolumeManager(outputType: OutputType, windowWidth: Int, windowHeight: Int, scene: Scene) {

        this.outputType = outputType
        if (outputType == OutputType.REGULAR_IMAGE) {
            volumeManager = createVolumeManager("ComputeVolume.comp")
            volumeManager.customTextures.add("OutputRender")

            val outputBuffer = MemoryUtil.memCalloc(windowWidth*windowHeight*4)
            val outputTexture = Texture.fromImage(Image(outputBuffer, windowWidth, windowHeight), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
            volumeManager.material().textures["OutputRender"] = outputTexture

            colorTexture = volumeManager.material().textures["OutputRender"]

            volumeManager.customUniforms.add("fixedStepSize")
            volumeManager.shaderProperties["fixedStepSize"] = true
            volumeManager.customUniforms.add("stepsPerVoxel")
            volumeManager.shaderProperties["stepsPerVoxel"] = 2
        } else if (outputType == OutputType.LAYERED_IMAGE) {
            volumeManager = createVolumeManager("ComputeNonConvex.comp", "AccumulateNonConvex.comp")
            volumeManager.customTextures.add("LayeredColors")
            volumeManager.customTextures.add("LayeredDepths")

            val layeredColorBuffer = MemoryUtil.memCalloc(windowWidth*windowHeight*4*NUM_LAYERS)
            var layeredColorTexture: Texture = Texture.fromImage(Image(layeredColorBuffer, NUM_LAYERS, windowWidth, windowHeight),
                usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
            volumeManager.material().textures["LayeredColors"] = layeredColorTexture

            val layeredDepthBuffer = MemoryUtil.memCalloc(windowWidth*windowHeight*4*NUM_LAYERS)
            var layeredDepthTexture: Texture = Texture.fromImage(Image(layeredDepthBuffer, NUM_LAYERS, windowWidth, windowHeight, FloatType()),
                usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), channels = 1, mipmap = false,
                normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)

            volumeManager.material().textures["LayeredDepths"] = layeredDepthTexture

            colorTexture = volumeManager.material().textures["LayeredColors"]
            depthTexture = volumeManager.material().textures["LayeredDepths"]

            volumeManager.customUniforms.add("fixedStepSize")
            volumeManager.shaderProperties["fixedStepSize"] = true
            volumeManager.customUniforms.add("stepsPerVoxel")
            volumeManager.shaderProperties["stepsPerVoxel"] = 2
        } else if (outputType == OutputType.VDI) {
            vdiVolumeManager = VDIVolumeManager(hub, windowWidth, windowHeight, NUM_LAYERS, scene)
            volumeManager = vdiVolumeManager!!.createVDIVolumeManager(vdiFull = false)
            colorTexture = vdiVolumeManager!!.getColorTextureOrNull()
            depthTexture = vdiVolumeManager!!.getDepthTextureOrNull()
            firstPassTexture = vdiVolumeManager!!.getNumGeneratedTextureOrNull()
        } else if (outputType == OutputType.TEST_VDI_FULL) {
            vdiVolumeManager = VDIVolumeManager(hub, windowWidth, windowHeight, NUM_LAYERS, scene)
            volumeManager = vdiVolumeManager!!.createVDIVolumeManager(vdiFull = true)
            colorTexture = vdiVolumeManager!!.getColorTextureOrNull()
            depthTexture = vdiVolumeManager!!.getDepthTextureOrNull()
        }
        hub.add(volumeManager)
    }

}