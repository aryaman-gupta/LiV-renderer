import graphics.scenery.natives.IceTWrapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class IceTWrapperTest {

    @Test
    fun createNativeContextReturnsValidHandle() {
        val handle = IceTWrapper.createNativeContext()
        assertTrue(handle > 0)
    }

    @Test
    fun destroyNativeContextWithValidHandle() {
        val handle = IceTWrapper.createNativeContext()
        IceTWrapper.destroyNativeContext(handle)
        // Assuming no exception means success
    }

    @Test
    fun setupICETWithValidParameters() {
        val handle = IceTWrapper.createNativeContext()
        IceTWrapper.setupICET(handle, 800, 600)
        // Assuming no exception means success
    }

    @Test
    fun setProcessorCentroidWithValidParameters() {
        val handle = IceTWrapper.createNativeContext()
        val positions = floatArrayOf(1.0f, 2.0f, 3.0f)
        IceTWrapper.setProcessorCentroid(handle, 1, positions)
        // Assuming no exception means success
    }

    @Test
    fun compositeFrameWithValidParameters() {
        val handle = IceTWrapper.createNativeContext()
        val subImage = java.nio.ByteBuffer.allocate(1024)
        val camPos = floatArrayOf(0.0f, 0.0f, 0.0f)
        IceTWrapper.compositeFrame(handle, subImage, camPos, 800, 600)
        // Assuming no exception means success
    }

    @Test
    fun createNativeContextReturnsNonZeroHandle() {
        val handle = IceTWrapper.createNativeContext()
        assertNotEquals(0, handle)
    }

    @Test
    fun destroyNativeContextWithInvalidHandle() {
        assertThrows(IllegalArgumentException::class.java) {
            IceTWrapper.destroyNativeContext(-1)
        }
    }

    @Test
    fun setupICETWithInvalidDimensions() {
        val handle = IceTWrapper.createNativeContext()
        assertThrows(IllegalArgumentException::class.java) {
            IceTWrapper.setupICET(handle, -800, 600)
        }
    }

    @Test
    fun setProcessorCentroidWithInvalidProcessorID() {
        val handle = IceTWrapper.createNativeContext()
        val positions = floatArrayOf(1.0f, 2.0f, 3.0f)
        assertThrows(IllegalArgumentException::class.java) {
            IceTWrapper.setProcessorCentroid(handle, -1, positions)
        }
    }

    @Test
    fun compositeFrameWithInvalidBuffer() {
        val handle = IceTWrapper.createNativeContext()
        val subImage = java.nio.ByteBuffer.allocate(0)
        val camPos = floatArrayOf(0.0f, 0.0f, 0.0f)
        assertThrows(IllegalArgumentException::class.java) {
            IceTWrapper.compositeFrame(handle, subImage, camPos, 800, 600)
        }
    }
}