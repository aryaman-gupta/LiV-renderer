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

    // You may store other relevant data as needed:
    // e.g., you might store communicator rank, etc.
    // int rank;
    // ...
};

int distributeVariable(int *counts, int *countsRecv, void *sendBuf, void *recvBuf,
                       int commSize, const std::string &purpose = "")
{
#if VERBOSE
    std::cout << "Performing distribution of " << purpose << std::endl;
#endif
    // All-to-all for counts
    MPI_Alltoall(counts, 1, MPI_INT, countsRecv, 1, MPI_INT, visualizationComm);

    // set up the AllToAllv
    int displacementSendSum = 0;
    int *displacementSend   = new int[commSize];
    int displacementRecvSum = 0;
    int *displacementRecv   = new int[commSize];

    int rank;
    MPI_Comm_rank(visualizationComm, &rank);

    for(int i = 0; i < commSize; i++) {
        displacementSend[i] = displacementSendSum;
        displacementSendSum += counts[i];

        displacementRecv[i] = displacementRecvSum;
        displacementRecvSum += countsRecv[i];
    }

    if (recvBuf == nullptr) {
        std::cout << "This is an error! Receive buffer was null. Allocating now..."
                  << std::endl;
        int sum = 0;
        for(int i = 0; i < commSize; i++) {
            sum += countsRecv[i];
        }
        recvBuf = malloc(sum);  // Not recommended in real usage;
                                // better to allocate up front or check capacity
    }

    MPI_Alltoallv(sendBuf, counts, displacementSend, MPI_BYTE,
                  recvBuf, countsRecv, displacementRecv, MPI_BYTE,
                  visualizationComm);

    delete[] displacementSend;
    delete[] displacementRecv;

    return displacementRecvSum; // total bytes received
}

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

    // If MPI not yet initialized, do so here (optional)
    // int argc = 0;
    // char** argv = nullptr;
    // MPI_Init(&argc, &argv);

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

    // Return pointer as a jlong
    return reinterpret_cast<jlong>(resources);
}

// -------------------- 2) distributeColorVDI ---------------------------
JNIEXPORT jobject JNICALL
Java_graphics_scenery_natives_VDIMPIWrapper_distributeColorVDI(
    JNIEnv* env, jobject obj,
    jlong nativeHandle,
    jobject colorVDI,         // local color buffer from Kotlin
    jintArray supersegmentCounts,
    jint commSize)
{
    VDIResources* res = reinterpret_cast<VDIResources*>(nativeHandle);
    if(!res) {
        // Could throw an exception in real code.
        return nullptr;
    }

    // Grab local color data + size
    void* localPtr   = env->GetDirectBufferAddress(colorVDI);
    jlong localBytes = env->GetDirectBufferCapacity(colorVDI);

    // Extract the supersegment counts
    jint* supsegCounts = env->GetIntArrayElements(supersegmentCounts, nullptr);
    int   arrayLen     = env->GetArrayLength(supersegmentCounts);

    // Build arrays for color counts
    int *colorCounts     = new int[commSize];
    int *colorCountsRecv = new int[commSize];

    // For each rank, compute how many bytes we send (replicating your logic).
    // e.g.,  supsegCounts[i] * 4 * 4
    for(int i = 0; i < commSize; i++) {
        colorCounts[i] = supsegCounts[i] * 4 * 4;
    }

    // Now call distributeVariable
#if PROFILING
    auto begin = std::chrono::high_resolution_clock::now();
#endif

    int totalRecvdColor = distributeVariable(
        colorCounts, colorCountsRecv,
        localPtr,        // sendBuf
        res->colorPtr,   // recvBuf  (already allocated in init)
        commSize,
        "color"
    );

#if VERBOSE
    std::cout << "Distribute color received " << totalRecvdColor << " bytes" << std::endl;
#endif

#if PROFILING
    auto end   = std::chrono::high_resolution_clock::now();
    double localTime = std::chrono::duration<double>(end - begin).count();
    // ... do MPI_Reduce, store times, etc. if you want
#endif

    // Release pinned array
    env->ReleaseIntArrayElements(supersegmentCounts, supsegCounts, JNI_ABORT);
    delete[] colorCounts;
    delete[] colorCountsRecv;

    // Return a ByteBuffer pointing to the color buffer in the struct.
    // We typically pass how many bytes we actually used, e.g. totalRecvdColor
    // but be mindful it might not exceed res->colorCap
    jlong usedBytes = (totalRecvdColor < res->colorCap)
                    ? totalRecvdColor
                    : res->colorCap;
    if(usedBytes < 0) usedBytes = 0;

    return env->NewDirectByteBuffer(res->colorPtr, usedBytes);
}

// -------------------- 3) distributeDepthVDI ---------------------------
JNIEXPORT jobject JNICALL
Java_graphics_scenery_natives_VDIMPIWrapper_distributeDepthVDI(
    JNIEnv* env, jobject obj,
    jlong nativeHandle,
    jobject depthVDI,
    jintArray supersegmentCounts,
    jint commSize)
{
    VDIResources* res = reinterpret_cast<VDIResources*>(nativeHandle);
    if(!res) {
        return nullptr;
    }

    // Local depth data
    void* localPtr   = env->GetDirectBufferAddress(depthVDI);
    jlong localBytes = env->GetDirectBufferCapacity(depthVDI);

    jint* supsegCounts = env->GetIntArrayElements(supersegmentCounts, nullptr);

    // Compute depth counts
    int* depthCounts     = new int[commSize];
    int* depthCountsRecv = new int[commSize];

    for(int i = 0; i < commSize; i++) {
        depthCounts[i] = supsegCounts[i] * 4 * 2;  // from original code
    }

    // MPI distribution
    int totalRecvdDepth = distributeVariable(
        depthCounts, depthCountsRecv,
        localPtr,         // sendBuf
        res->depthPtr,    // recvBuf
        commSize,
        "depth"
    );

#if VERBOSE
    std::cout << "Distribute depth received " << totalRecvdDepth << " bytes" << std::endl;
#endif

    env->ReleaseIntArrayElements(supersegmentCounts, supsegCounts, JNI_ABORT);
    delete[] depthCounts;
    delete[] depthCountsRecv;

    jlong usedBytes = (totalRecvdDepth < res->depthCap)
                    ? totalRecvdDepth
                    : res->depthCap;
    if(usedBytes < 0) usedBytes = 0;

    return env->NewDirectByteBuffer(res->depthPtr, usedBytes);
}

// -------------------- 4) distributePrefixVDI --------------------------
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

    void* localPtr   = env->GetDirectBufferAddress(prefixVDI);
    jlong localBytes = env->GetDirectBufferCapacity(prefixVDI);

#if VERBOSE
    std::cout << "Distributing prefix with window "
              << windowWidth << "x" << windowHeight << std::endl;
#endif

    int prefixImageSize = nativeHandle->prefixCap;

     MPI_Alltoall(localPtr, prefixImageSize/commSize, MPI_BYTE,
                  res->prefixPtr, prefixImageSize/commSize, MPI_BYTE,
                  visualizationComm);

    // Return ByteBuffer
    return env->NewDirectByteBuffer(res->prefixPtr, prefixImageSize);
}

// -------------------- 5) releaseVDIResources --------------------------
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

    // If you own MPI lifetime, you might finalize it here:
    // MPI_Finalize();

    delete res;
}

} // extern "C"
