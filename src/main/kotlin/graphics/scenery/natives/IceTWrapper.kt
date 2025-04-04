package graphics.scenery.natives

import java.nio.ByteBuffer

object IceTWrapper {
    init {
        System.loadLibrary("IceTJNIBridge")
    }

    // Create/destroy
    external fun createNativeContext(): Long
    external fun destroyNativeContext(handle: Long)

    // Setup
    external fun setupICET(handle: Long, width: Int, height: Int)

    // Set centroids
    external fun setProcessorCentroid(handle: Long, processorID: Int, positions: FloatArray)

    // Composite
    external fun compositeFrame(
        handle: Long,
        subImage: ByteBuffer,
        camPos: FloatArray,
        width: Int,
        height: Int
    ) : ByteBuffer?

    external fun compositeLayered(
        handle: Long,
        colorBuffer: ByteBuffer,
        depthBuffer: ByteBuffer,
        width: Int,
        height: Int,
        numLayers: Int
    ) : ByteBuffer?
}