#include <jni.h>
#include <mpi.h>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <chrono>

// Adapt as needed:
// If your original code had a namespace or used a custom communicator:
static MPI_Comm visualizationComm = MPI_COMM_WORLD;
// Or retrieve your communicator however you do in your real code.

// Uncomment if you rely on these preprocessor flags in your code:
// #define VERBOSE 1
// #define PROFILING 1

// ---------------------------------------------------------------------
//  VDIResources struct
// ---------------------------------------------------------------------
struct VDIResources {
    // Buffers for color, depth, and prefix
    void* colorPtr  = nullptr;
    int   colorCap  = 0;   // capacity in bytes

    void* depthPtr  = nullptr;
    int   depthCap  = 0;

    void* prefixPtr = nullptr;
    int   prefixCap = 0;

    bool communicatorSelfInitialized = false;
};

// ---------------------------------------------------------------------
//  JNI methods
// ---------------------------------------------------------------------

extern "C" {

// -------------------- 1) initializeVDIResources -----------------------
JNIEXPORT jlong JNICALL
Java_graphics_scenery_natives_VDIMPIWrapper_initializeVDIResources(
    JNIEnv* env, jobject obj,
    jint colorCapacity,
    jint depthCapacity,
    jint prefixCapacity)
{
#if VERBOSE
    std::cout << "Initializing VDI resources" << std::endl;
#endif

    // Allocate the struct
    VDIResources* resources = new VDIResources();

    // Allocate buffers
    resources->colorPtr  = new char[colorCapacity];
    resources->colorCap  = colorCapacity;

    resources->depthPtr  = new char[depthCapacity];
    resources->depthCap  = depthCapacity;

    resources->prefixPtr = new char[prefixCapacity];
    resources->prefixCap = prefixCapacity;

#if VERBOSE
    std::cout << "Allocated color=" << colorCapacity
              << " depth=" << depthCapacity
              << " prefix=" << prefixCapacity << " bytes." << std::endl;
#endif

    int mpi_initialized = 0;
    MPI_Initialized(&mpi_initialized);
    if (!mpi_initialized) {
        int argc = 0;
        char** argv = nullptr;
        MPI_Init(&argc, &argv);
        resources->communicatorSelfInitialized = true;
    }

    // Return pointer as a jlong
    return reinterpret_cast<jlong>(resources);
}

// -------------------- 2) distributeSupersegmentCounts -----------------
JNIEXPORT jintArray JNICALL
Java_graphics_scenery_natives_VDIMPIWrapper_distributeSupersegmentCounts(
    JNIEnv* env, jobject obj,
    jlong nativeHandle,
    jintArray supersegmentCounts,
    jint commSize)
{
    // Extract the supersegment counts
    jint* supsegCounts = env->GetIntArrayElements(supersegmentCounts, nullptr);

    // Create a new array to receive the distributed counts
    jintArray result = env->NewIntArray(commSize);
    jint* receivedCounts = env->GetIntArrayElements(result, nullptr);

    // Perform MPI_Alltoall to distribute the counts
    MPI_Alltoall(supsegCounts, 1, MPI_INT, receivedCounts, 1, MPI_INT, visualizationComm);

    // Release the arrays
    env->ReleaseIntArrayElements(supersegmentCounts, supsegCounts, JNI_ABORT);
    env->ReleaseIntArrayElements(result, receivedCounts, 0);

    return result;
}

// -------------------- 3) distributeColorVDI ---------------------------
JNIEXPORT jobject JNICALL
Java_graphics_scenery_natives_VDIMPIWrapper_distributeColorVDI(
    JNIEnv* env, jobject obj,
    jlong nativeHandle,
    jobject colorVDI,         // local color buffer from Kotlin
    jintArray counts,         // pre-calculated counts
    jintArray displacements,  // pre-calculated displacements
    jintArray countsRecv,     // pre-calculated receive counts
    jintArray displacementsRecv, // pre-calculated receive displacements
    jint commSize)
{
    VDIResources* res = reinterpret_cast<VDIResources*>(nativeHandle);
    if(!res) {
        // Could throw an exception in real code.
        return nullptr;
    }

    // Grab local color data
    void* localPtr = env->GetDirectBufferAddress(colorVDI);

    // Extract the arrays
    jint* countsArray = env->GetIntArrayElements(counts, nullptr);
    jint* displacementsArray = env->GetIntArrayElements(displacements, nullptr);
    jint* countsRecvArray = env->GetIntArrayElements(countsRecv, nullptr);
    jint* displacementsRecvArray = env->GetIntArrayElements(displacementsRecv, nullptr);

    // Calculate total bytes to receive
    int totalRecvdColor = 0;
    for (int i = 0; i < commSize; i++) {
        totalRecvdColor += countsRecvArray[i];
    }

#if PROFILING
    auto begin = std::chrono::high_resolution_clock::now();
#endif

    // Perform MPI_Alltoallv
    MPI_Alltoallv(
        localPtr, countsArray, displacementsArray, MPI_BYTE,
        res->colorPtr, countsRecvArray, displacementsRecvArray, MPI_BYTE,
        visualizationComm
    );

#if VERBOSE
    std::cout << "Distribute color received " << totalRecvdColor << " bytes" << std::endl;
#endif

#if PROFILING
    auto end = std::chrono::high_resolution_clock::now();
    double localTime = std::chrono::duration<double>(end - begin).count();
    // ... do MPI_Reduce, store times, etc. if you want
#endif

    // Release arrays
    env->ReleaseIntArrayElements(counts, countsArray, JNI_ABORT);
    env->ReleaseIntArrayElements(displacements, displacementsArray, JNI_ABORT);
    env->ReleaseIntArrayElements(countsRecv, countsRecvArray, JNI_ABORT);
    env->ReleaseIntArrayElements(displacementsRecv, displacementsRecvArray, JNI_ABORT);

    // Return a ByteBuffer pointing to the color buffer in the struct.
    jlong usedBytes = (totalRecvdColor < res->colorCap)
                    ? totalRecvdColor
                    : res->colorCap;
    if(usedBytes < 0) usedBytes = 0;

    return env->NewDirectByteBuffer(res->colorPtr, usedBytes);
}

// -------------------- 4) distributeDepthVDI ---------------------------
JNIEXPORT jobject JNICALL
Java_graphics_scenery_natives_VDIMPIWrapper_distributeDepthVDI(
    JNIEnv* env, jobject obj,
    jlong nativeHandle,
    jobject depthVDI,
    jintArray counts,         // pre-calculated counts
    jintArray displacements,  // pre-calculated displacements
    jintArray countsRecv,     // pre-calculated receive counts
    jintArray displacementsRecv, // pre-calculated receive displacements
    jint commSize)
{
    VDIResources* res = reinterpret_cast<VDIResources*>(nativeHandle);
    if(!res) {
        return nullptr;
    }

    // Local depth data
    void* localPtr = env->GetDirectBufferAddress(depthVDI);

    // Extract the arrays
    jint* countsArray = env->GetIntArrayElements(counts, nullptr);
    jint* displacementsArray = env->GetIntArrayElements(displacements, nullptr);
    jint* countsRecvArray = env->GetIntArrayElements(countsRecv, nullptr);
    jint* displacementsRecvArray = env->GetIntArrayElements(displacementsRecv, nullptr);

    // Calculate total bytes to receive
    int totalRecvdDepth = 0;
    for (int i = 0; i < commSize; i++) {
        totalRecvdDepth += countsRecvArray[i];
    }

    // Perform MPI_Alltoallv
    MPI_Alltoallv(
        localPtr, countsArray, displacementsArray, MPI_BYTE,
        res->depthPtr, countsRecvArray, displacementsRecvArray, MPI_BYTE,
        visualizationComm
    );

#if VERBOSE
    std::cout << "Distribute depth received " << totalRecvdDepth << " bytes" << std::endl;
#endif

    // Release arrays
    env->ReleaseIntArrayElements(counts, countsArray, JNI_ABORT);
    env->ReleaseIntArrayElements(displacements, displacementsArray, JNI_ABORT);
    env->ReleaseIntArrayElements(countsRecv, countsRecvArray, JNI_ABORT);
    env->ReleaseIntArrayElements(displacementsRecv, displacementsRecvArray, JNI_ABORT);

    jlong usedBytes = (totalRecvdDepth < res->depthCap)
                    ? totalRecvdDepth
                    : res->depthCap;
    if(usedBytes < 0) usedBytes = 0;

    return env->NewDirectByteBuffer(res->depthPtr, usedBytes);
}

// -------------------- 5) distributePrefixVDI --------------------------
JNIEXPORT jobject JNICALL
Java_graphics_scenery_natives_VDIMPIWrapper_distributePrefixVDI(
    JNIEnv* env, jobject obj,
    jlong nativeHandle,
    jobject prefixVDI,
    jint commSize)
{
    VDIResources* res = reinterpret_cast<VDIResources*>(nativeHandle);
    if(!res) {
        return nullptr;
    }

    void* localPtr = env->GetDirectBufferAddress(prefixVDI);
    jlong localBytes = env->GetDirectBufferCapacity(prefixVDI);

    // Perform MPI_Alltoall
    MPI_Alltoall(
        localPtr, localBytes/commSize, MPI_BYTE,
        res->prefixPtr, localBytes/commSize, MPI_BYTE,
        visualizationComm
    );

    // Return ByteBuffer
    return env->NewDirectByteBuffer(res->prefixPtr, localBytes);
}

// -------------------- 6) releaseVDIResources --------------------------
JNIEXPORT void JNICALL
Java_graphics_scenery_natives_VDIMPIWrapper_releaseVDIResources(
    JNIEnv* env, jobject obj,
    jlong nativeHandle)
{
    VDIResources* res = reinterpret_cast<VDIResources*>(nativeHandle);
    if(!res) {
        return;
    }

#if VERBOSE
    std::cout << "Releasing VDI resources" << std::endl;
#endif

    // Free the buffers
    delete[] reinterpret_cast<char*>(res->colorPtr);
    delete[] reinterpret_cast<char*>(res->depthPtr);
    delete[] reinterpret_cast<char*>(res->prefixPtr);

    if(res->communicatorSelfInitialized) {
        MPI_Finalize();
    }

    delete res;
}

/*
 * Class:     graphics_scenery_natives_VDIMPIWrapper
 * Method:    getCommSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_graphics_scenery_natives_VDIMPIWrapper_getCommSize(JNIEnv* env, jclass clazz) {
    int comm_size = 0;
    MPI_Comm_size(MPI_COMM_WORLD, &comm_size);
    return comm_size;
}

} // extern "C"

