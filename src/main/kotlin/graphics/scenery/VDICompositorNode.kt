package graphics.scenery

import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.vdi.VDINode
import net.imglib2.type.numeric.real.FloatType
import org.joml.Matrix4f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil

class VDICompositorNode(windowWidth: Int, windowHeight: Int, numSupersegments: Int, mpiCommSize: Int) : RichNode() {

    // camera parameters
    @ShaderProperty
    var ProjectionOriginal = Matrix4f()

    @ShaderProperty
    var invProjectionOriginal = Matrix4f()

    @ShaderProperty
    var ViewOriginal = Matrix4f()

    @ShaderProperty
    var invViewOriginal = Matrix4f()

    // volume rendering scene parameter, the step size
    @ShaderProperty
    var nw = 0f

    @ShaderProperty
    var doComposite = false

    @ShaderProperty
    var numProcesses = 0

    @ShaderProperty
    var totalSupersegmentsFrom = IntArray(50); // the total supersegments received from a given PE

    // vdi parameters
    @ShaderProperty
    var isCompact = true

    @ShaderProperty
    var vdiWidth = 0

    @ShaderProperty
    var vdiHeight = 0

    init {
        name = "VDICompositorNode"
        setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("VDICompositor.comp"), this@VDICompositorNode::class.java)))

        // set up the output textures
        val outputColours = MemoryUtil.memCalloc(VDINode.getColorBufferSize(windowWidth, windowHeight, numSupersegments) / mpiCommSize)
        val outputDepths = MemoryUtil.memCalloc(VDINode.getDepthBufferSize(windowWidth, windowHeight, numSupersegments) / mpiCommSize)
        val compositedVDIColor = VDINode.generateColorTexture(windowWidth, windowHeight, numSupersegments, outputColours)
        val compositedVDIDepth = VDINode.generateDepthTexture(windowWidth, windowHeight, numSupersegments, outputDepths)
        material().textures[compositedColorName] = compositedVDIColor
        material().textures[compositedDepthName] = compositedVDIDepth

        metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(windowWidth/mpiCommSize, windowHeight, 1)
        )
        vdiWidth = windowWidth
        vdiHeight = windowHeight

        numProcesses = mpiCommSize

        isCompact = true
    }

    companion object {
        val compositedColorName = "CompositedVDIColor"
        val compositedDepthName = "CompositedVDIDepth"
    }

}