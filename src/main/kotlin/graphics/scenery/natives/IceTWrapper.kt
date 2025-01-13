package graphics.scenery.natives

object IceTWrapper {
    init {
        System.loadLibrary("icetwrapper")  // Your .so/.dylib name
    }

    // Create/destroy
    external fun createNativeContext(): Long
    external fun destroyNativeContext(handle: Long)

    // Setup
    external fun setupICET(handle: Long, width: Int, height: Int)

    // Set centroids
    external fun setCentroids(handle: Long, positions: Array<FloatArray>)

    // Composite
    external fun compositeFrame(
        handle: Long,
        subImage: java.nio.ByteBuffer,
        camPos: FloatArray,
        width: Int,
        height: Int
    )
}