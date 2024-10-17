package graphics.scenery

import org.joml.Matrix4f

class VDICompositorNode : RichNode() {

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
    var windowWidth = 0

    @ShaderProperty
    var windowHeight = 0

    init {

    }

}