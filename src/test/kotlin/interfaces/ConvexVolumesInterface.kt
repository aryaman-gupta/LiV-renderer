package graphics.scenery.tests.interfaces

import graphics.scenery.VolumeManagerManager
import graphics.scenery.interfaces.RenderingInterfaceBase
import graphics.scenery.parallelization.ConvexVolumesParallelization
import graphics.scenery.parallelization.ParallelizationBase
import org.joml.Vector3f

class ConvexVolumesInterface(wWidth: Int, wHeight: Int, rank: Int, commSize: Int, nodeRank: Int) : RenderingInterfaceBase("ConvexVolumes", wWidth, wHeight, rank, commSize, nodeRank) {

    override var outputProcessingType = OutputProcessingType.STREAM

    val processorOrigins = mutableMapOf<Int, Vector3f>()
    val processorDimensions = mutableMapOf<Int, Vector3f>()

    @Suppress("unused")
    fun addProcessorData(processorId: Int, origin: FloatArray, dimensions: FloatArray) {
        processorOrigins[processorId] = Vector3f(origin[0] * pixelToWorld, origin[1] * -1 * pixelToWorld, origin[2] * pixelToWorld)
        processorDimensions[processorId] = Vector3f(dimensions[0], dimensions[1], dimensions[2])
    }

    override fun initializeParallelizationScheme(): ParallelizationBase {
        return ConvexVolumesParallelization(volumeManagerManager, mpiParameters, scene)
    }

    override fun setupVolumeManagerManager() {
        volumeManagerManager = VolumeManagerManager(hub)
        volumeManagerManager.instantiateVolumeManager(
            VolumeManagerManager.OutputType.REGULAR_IMAGE,
            windowWidth, windowHeight,
            scene
        )
    }

    override fun additionalSceneSetup() {
        if (processorOrigins.size != mpiParameters.commSize || processorDimensions.size != mpiParameters.commSize) {
            throw IllegalArgumentException("For rendering simple (i.e. convex) volume decompositions, please provide the origin and dimensions of the data for each processor" +
                    " using the addProcessorData method.")
        }

        //calculate centroids of each processor
        val processorCentroids = mutableMapOf<Int, Vector3f>()
        processorOrigins.forEach { (processorId, origin) ->
            val dimensions = processorDimensions[processorId] ?: throw IllegalArgumentException("No dimensions provided for processor $processorId")

            val frontTopRight = Vector3f(origin)

            frontTopRight.x += (dimensions.x * pixelToWorld)
            frontTopRight.y -= (dimensions.y * pixelToWorld) //y is inverted in scenery scenegraph
            frontTopRight.z += (dimensions.z * pixelToWorld)

            processorCentroids.put(processorId, Vector3f(origin).add(frontTopRight).mul(0.5f))

        }
        (parallelizationScheme as ConvexVolumesParallelization).setCentroids(processorCentroids)
    }
}