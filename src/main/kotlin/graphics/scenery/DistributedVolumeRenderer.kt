package graphics.scenery

import bvv.core.shadergen.generate.SegmentTemplate
import bvv.core.shadergen.generate.SegmentType
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.vulkan.VulkanRenderer
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.textures.Texture
import graphics.scenery.ui.SwingBridgeFrame
import graphics.scenery.utils.Image
import graphics.scenery.utils.SystemHelpers
import graphics.scenery.utils.extensions.fetchTexture
import graphics.scenery.volumes.*
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 *
 */
class DistributedVolumeRenderer : SceneryBase("Distributed Volume Renderer", windowWidth = 1280, windowHeight = 720) {

    var volumes: HashMap<Int, BufferedVolume?> = java.util.HashMap()

    var hmd: TrackedStereoGlasses? = null
    val cam: Camera = DetachedHeadCamera(hmd)

    val plane = FullscreenObject()

    var volumeDims = Vector3f(0f)

    var isCluster = false
    var benchmarking = false
    var dataset = ""

    var volumeManagerInitialized = AtomicBoolean(false)

    var pixelToWorld = 0.001f

    var commSize = 1
    var rank = 0
    var nodeRank = 0
    var rendererConfigured = false

    var imagePointer = 0L
    var mpiPointer = 0L

    var volumesCreated = AtomicBoolean(false)

    private external fun compositeImages(
        subImage: ByteBuffer,
        myRank: Int,
        commSize: Int,
        camPos: FloatArray,
        imagePointer: Long
    )

    @Suppress("unused")
    fun setVolumeDims(dims: IntArray) {
        volumeDims = Vector3f(dims[0].toFloat(), dims[1].toFloat(), dims[2].toFloat())
    }

    @Suppress("unused")
    fun addVolume(volumeID: Int, dimensions: IntArray, pos: FloatArray, is16bit: Boolean) {
        logger.info("Trying to add the volume")
        logger.info("id: $volumeID, dims: ${dimensions[0]}, ${dimensions[1]}, ${dimensions[2]} pos: ${pos[0]}, ${pos[1]}, ${pos[2]}")

        while (!volumeManagerInitialized.get()) {
            Thread.sleep(50)
        }

        val volume = if (is16bit) {
            Volume.fromBuffer(emptyList(), dimensions[0], dimensions[1], dimensions[2], UnsignedShortType(), hub)
        } else {
            Volume.fromBuffer(emptyList(), dimensions[0], dimensions[1], dimensions[2], UnsignedByteType(), hub)
        }
        volume.spatial().position = Vector3f(pos[0], pos[1], pos[2])

        volume.origin = Origin.FrontBottomLeft
        volume.spatial().needsUpdate = true
        volume.colormap = Colormap.get("rbdarker")
        volume.pixelToWorldRatio = pixelToWorld

        val tf = TransferFunction()

        with(tf) {
            addControlPoint(0.0f, 0.95f)
            addControlPoint(0.15f, 0.0f)
            addControlPoint(0.45f, 0.0f)
            addControlPoint(0.5f, 0.35f)
            addControlPoint(0.55f, 0.0f)
            addControlPoint(0.80f, 0.0f)
            addControlPoint(1.0f, 0.378f)
        }

        volume.name = "volume"

        volume.transferFunction = tf

        scene.addChild(volume)

        volumes[volumeID] = volume
        volumesCreated.set(true)
    }

    @Suppress("unused")
    fun updateVolume(volumeID: Int, buffer: ByteBuffer) {
        while (volumes[volumeID] == null) {
            logger.info("Waiting for volume $volumeID to be created")
            Thread.sleep(50)
        }
        logger.info("Volume $volumeID has been updated")
        volumes[volumeID]?.addTimepoint("t", buffer)
        volumes[volumeID]?.goToLastTimepoint()
    }

    val nonConvex = true

    override fun init() {
        logger.info("setting renderer device id to: $nodeRank")
//        System.setProperty("scenery.Renderer.DeviceId", nodeRank.toString())
        System.setProperty("scenery.Renderer.DeviceId", "1")

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        setVolumeDims(intArrayOf(1024, 1024, 795))
        pixelToWorld = 3.84f / volumeDims.x

        val volumeManagerManager = VolumeManagerManager(hub)

        if (!nonConvex) {
            volumeManagerManager.instantiateVolumeManager(
                VolumeManagerManager.OutputType.REGULAR_IMAGE,
                windowWidth, windowHeight
            )

            scene.addChild(plane)
            plane.material().textures["diffuse"] = volumeManagerManager.getColorTextureOrNull()!!
        } else {
            volumeManagerManager.instantiateVolumeManager(
                VolumeManagerManager.OutputType.LAYERED_IMAGE,
                windowWidth, windowHeight
            )
        }

        volumeManagerInitialized.set(true)


        with(cam) {
            spatial().position = Vector3f(-2.300E+0f, -6.402E+0f, 1.100E+0f) //V1 for Rayleigh_Taylor
            spatial().rotation = Quaternionf(2.495E-1, -7.098E-1, 3.027E-1, -5.851E-1)

            perspectiveCamera(50.0f, windowWidth, windowHeight)
            cam.farPlaneDistance = 20.0f
        }
        scene.addChild(cam)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.spatial().position = Vector3f(2.0f * i - 4.0f, i - 1.0f, 0.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 0.5f
            scene.addChild(light)
        }

        while (!rendererConfigured) {
            Thread.sleep(50)
        }

        logger.info("Exiting init function!")

//        renderer!!.recordMovie("Gray_scott.mp4")


        thread {
            manageDVR(volumeManagerManager)
        }

        thread {
            while (!volumesCreated.get()) {
                Thread.sleep(50)
            }

            val bridge = SwingBridgeFrame("1DTransferFunctionEditor")
            val tfUI = TransferFunctionEditor(volumes[0] as Volume)
            bridge.addPanel(tfUI)
            tfUI.name = volumes[0]?.name ?: ""
            val swingUiNode = bridge.uiNode
            swingUiNode.spatial() {
                position = Vector3f(2f, 0f, 0f)
            }

            scene.addChild(swingUiNode)
        }
    }

    fun manageDVR(volumeManagerManager: VolumeManagerManager) {

        while (renderer?.firstImageReady == false) {
            Thread.sleep(50)
        }

        while (!rendererConfigured) {
            Thread.sleep(50)
        }
        var cnt = 0
        while (renderer?.shouldClose == false) {
            Thread.sleep(2000)

            if (volumeManagerManager.getOutputType() == VolumeManagerManager.OutputType.REGULAR_IMAGE) {
                renderer?.screenshot("screenshot$cnt.png")
                cnt++
            } else if (volumeManagerManager.getOutputType() == VolumeManagerManager.OutputType.LAYERED_IMAGE) {
                val colorTexture = volumeManagerManager.getColorTextureOrNull()!!
                val depthTexture = volumeManagerManager.getDepthTextureOrNull()!!

                var textureFetched = Texture().fetchTexture(colorTexture)

                if (textureFetched < 0) {
                    logger.error("Error fetching layered color texture. return value: $textureFetched")
                }

                textureFetched = Texture().fetchTexture(depthTexture)

                if (textureFetched < 0) {
                    logger.error("Error fetching layered depth texture. return value: $textureFetched")
                }

                val colorBuffer = colorTexture.contents!!
                val depthBuffer = depthTexture.contents!!
                SystemHelpers.dumpToFile(colorBuffer, "color$cnt.raw")
                SystemHelpers.dumpToFile(depthBuffer, "depth$cnt.raw")
            }


//        val colorTexture = volumes[0]?.volumeManager?.material()?.textures?.get("OutputRender")!!
//
//
//        (renderer as VulkanRenderer).runAfterRendering.add {
//            val textureFetched = Texture().fetchTexture(colorTexture)
//
//            if(textureFetched < 0) {
//                logger.error("Error fetching DVR texture. return value: $textureFetched")
//            }
//
//            val imageBuffer = colorTexture.contents!!
//
//            val camPos = FloatArray(3)
//
//            camPos[0] = cam.spatial().position.x
//            camPos[1] = cam.spatial().position.y
//            camPos[2] = cam.spatial().position.z
//
//            if (imageBuffer.remaining() == windowWidth * windowHeight * 4) {
//                compositeImages(imageBuffer, rank, commSize, camPos, imagePointer) //this function will call a java fn that will place the image on the screen
//            } else {
//                logger.error("Not compositing because image size: ${imageBuffer.remaining()} expected: ${windowHeight * windowWidth * 4}")
//            }
//        }
        }
    }

    @Suppress("unused")
    fun displayComposited(compositedImage: ByteBuffer) {

        val bufferLE = compositedImage.order(ByteOrder.LITTLE_ENDIAN)
        plane.material().textures["diffuse"] =
            Texture(Vector3i(windowWidth, windowHeight, 1), 4, contents = bufferLE, mipmap = true)
    }

    @Suppress("unused")
    fun stopRendering() {
        renderer?.shouldClose = true
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            DistributedVolumeRenderer().main()
        }
    }
}