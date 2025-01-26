package graphics.scenery.tests

import graphics.scenery.tests.interfaces.ConvexVolumesInterface
import graphics.scenery.tests.interfaces.NaiveInterface
import java.io.File
import java.nio.ByteBuffer
import kotlin.concurrent.thread


fun main(args: Array<String>) {
    if (args.size < 5) {
        println("Usage: <program> <data file path> <dimX> <dimY> <dimZ> <is16bit?>")
        return
    }

    val dataFilePath = args[0]
    val volumeDims = intArrayOf(args[1].toInt(), args[2].toInt(), args[3].toInt())
    val is16bit = args[4].toBoolean()

    val instance = NaiveInterface(1280, 720, 0, 1, 0)

    thread {
        instance.main()
    }

    instance.waitRendererReady()

    instance.setVolumeDimensions(volumeDims)

    val scaling = instance.getVolumeScaling()

//    instance.addProcessorData(0, floatArrayOf(0f, 0f, 0f), volumeDims.map { it.toFloat() }.toFloatArray())

    // Add a volume
    val volumePosition = floatArrayOf(0.0f, 0.0f, 0.0f)

    instance.addVolume(0, volumeDims, volumePosition, is16bit)

    // Create a buffer to update the volume
    val bufferSize = volumeDims[0] * volumeDims[1] * volumeDims[2] * if (is16bit) 2 else 1
    val buffer = ByteBuffer.allocateDirect(bufferSize)

    // Read the data from the file
    val file = File(dataFilePath)
    val fileData = file.readBytes()
    buffer.put(fileData).flip()

    // Update the volume
    instance.updateVolume(0, buffer)

    instance.sceneSetupComplete.set(true)

    // Start the rendering loop
    while (true) {
        // Perform rendering tasks
        Thread.sleep(10000)
        instance.stopRendering()
    }
}
