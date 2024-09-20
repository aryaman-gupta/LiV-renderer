package graphics.scenery.tests

import graphics.scenery.DistributedVolumeRenderer
import graphics.scenery.SceneryElement
import graphics.scenery.backends.Renderer
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.math.log

fun main() {
    // Initialize the renderer
    val instance = DistributedVolumeRenderer()

    thread {
        instance.main()
        Thread.sleep(100000)
    }

    while (instance.hub.get(SceneryElement.Renderer)==null) {
        Thread.sleep(50)
    }

    // Set the volume dimensions
    val volumeDims = intArrayOf(256, 256, 256)
    instance.setVolumeDims(volumeDims)

    // Add a volume
    val volumeID = 0
    val volumePosition = floatArrayOf(0.0f, 0.0f, 0.0f)
    val is16bit = false
    instance.addVolume(volumeID, volumeDims, volumePosition, is16bit)

    // Create a buffer to update the volume
    val bufferSize = volumeDims[0] * volumeDims[1] * volumeDims[2]
    val buffer = ByteBuffer.allocateDirect(bufferSize)
    for (i in 0 until bufferSize) {
        buffer.put(i, (i % 256).toByte())
    }

    // Update the volume
    instance.updateVolume(volumeID, buffer)

    instance.rendererConfigured = true

    // Start the rendering loop
    while (true) {
        // Perform rendering tasks
        Thread.sleep(10000)
    }
}