package graphics.scenery.parallelization

import graphics.scenery.Camera
import graphics.scenery.VolumeManagerManager
import graphics.scenery.natives.IceTWrapper
import graphics.scenery.utils.SystemHelpers
import org.joml.Vector3f
import java.nio.ByteBuffer
import kotlin.io.path.createDirectories
import kotlin.io.path.Path

class NonConvexVolumesParallelization(volumeManagerManager: VolumeManagerManager, mpiParameters: MPIParameters, camera: Camera) : ParallelizationBase (volumeManagerManager, mpiParameters, camera) {

    override val twoPassRendering = false
    override val explicitCompositingStep = false

    val nativeContext = IceTWrapper.createNativeContext()
    /// Directory into which raw buffers will be stored.
    val outDir = Path("out/${System.getProperty("liv-renderer.BenchmarkDataset")}/${mpiParameters.commSize}")

    init {
        IceTWrapper.setupICET(nativeContext, windowWidth, windowHeight)
        outDir.createDirectories()
    }

    override fun distributeForCompositing(buffers: List<ByteBuffer>) {
        // call the ICET composite image function

        if (buffers.size != 2) {
            throw IllegalArgumentException("Expected exactly two buffers but got ${buffers.size}")
        }

        SystemHelpers.dumpToFile(buffers[0], "${outDir}/${mpiParameters.rank}-$frameNumber.color")
        SystemHelpers.dumpToFile(buffers[1], "${outDir}/${mpiParameters.rank}-$frameNumber.depth")

        val compositedColors = IceTWrapper.compositeLayered(nativeContext, buffers[0], buffers[1], windowWidth, windowHeight, 2)

        if (isRootProcess()) {
            // put the composited colors into the final composited buffer list
            compositedColors?.let {
                finalCompositedBuffers.add(compositedColors)
            }
        }
    }
}