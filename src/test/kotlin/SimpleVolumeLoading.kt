package graphics.scenery.tests

import graphics.scenery.tests.interfaces.NaiveInterface
import java.nio.ByteBuffer
import kotlin.concurrent.thread

fun main() {
    // Initialize the renderer
    val instance = NaiveInterface(1280, 720)

    thread {
        instance.main()

        //wait for some time
        Thread.sleep(100000)
    }

    instance.waitRendererReady()

    // Set the volume dimensions
    val volumeDims = intArrayOf(128, 128, 256)
    instance.setVolumeDimensions(volumeDims)

    val scaling = instance.getVolumeScaling()

    // Add a volume
    val volumePosition = floatArrayOf(0.0f, 0.0f, 0.0f)
    val is16bit = false
    instance.addVolume(0, volumeDims, volumePosition, is16bit)
    instance.addVolume(1, volumeDims, floatArrayOf(128f * scaling, 0f, 0f), is16bit)
    instance.addVolume(2, volumeDims, floatArrayOf(0f, -128f * scaling, 0f), is16bit)
    instance.addVolume(3, volumeDims, floatArrayOf(128f * scaling, -128f * scaling, 0f), is16bit)

    // Create a buffer to update the volume
    val bufferSize = volumeDims[0] * volumeDims[1] * volumeDims[2]
    val buffer = ByteBuffer.allocateDirect(bufferSize)
    for (i in 0 until bufferSize) {
        buffer.put(i, (128).toByte())
    }

    val buffer2 = ByteBuffer.allocateDirect(bufferSize)
    for (i in 0 until bufferSize) {
        buffer2.put(i, (255).toByte())
    }

    val buffer3 = ByteBuffer.allocateDirect(bufferSize)
    for (i in 0 until bufferSize) {
        buffer3.put(i, (0).toByte())
    }

    val buffer4 = ByteBuffer.allocateDirect(bufferSize)
    for (i in 0 until bufferSize) {
        buffer4.put(i, (128).toByte())
    }

    // Update the volume
    instance.updateVolume(0, buffer)
    instance.updateVolume(1, buffer2)
    instance.updateVolume(2, buffer3)
    instance.updateVolume(3, buffer4)

    instance.sceneSetupComplete.set(true)

    // Start the rendering loop
    while (true) {
        // Perform rendering tasks
        Thread.sleep(10000)
        instance.stopRendering()
    }
}