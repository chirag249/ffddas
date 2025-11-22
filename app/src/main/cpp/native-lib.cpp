#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>
#include <memory>

#define LOG_TAG "NativeLib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Helper function to convert Android Bitmap to OpenCV Mat
cv::Mat bitmapToMat(JNIEnv *env, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return cv::Mat();
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        LOGE("Unsupported bitmap format");
        return cv::Mat();
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return cv::Mat();
    }

    cv::Mat mat;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        mat = cv::Mat(info.height, info.width, CV_8UC4, pixels);
    } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        mat = cv::Mat(info.height, info.width, CV_8UC2, pixels);
    }

    // Clone the mat to ensure proper memory management
    cv::Mat result = mat.clone();

    AndroidBitmap_unlockPixels(env, bitmap);
    return result;
}

// Helper function to convert OpenCV Mat to Android Bitmap
bool matToBitmap(JNIEnv *env, cv::Mat &mat, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return false;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format");
        return false;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return false;
    }

    cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);

    // Ensure source matches RGBA for safe copy
    try {
        if (mat.type() == CV_8UC4) {
            mat.copyTo(tmp);
        } else if (mat.type() == CV_8UC3) {
            cv::Mat rgba;
            cv::cvtColor(mat, rgba, cv::COLOR_RGB2RGBA);
            rgba.copyTo(tmp);
        } else if (mat.type() == CV_8UC1) {
            cv::Mat rgba;
            cv::cvtColor(mat, rgba, cv::COLOR_GRAY2RGBA);
            rgba.copyTo(tmp);
        } else {
            LOGE("matToBitmap: Unsupported Mat type %d", mat.type());
            AndroidBitmap_unlockPixels(env, bitmap);
            return false;
        }
    } catch (const cv::Exception &e) {
        LOGE("matToBitmap: cv exception %s", e.what());
        AndroidBitmap_unlockPixels(env, bitmap);
        return false;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_ffddas_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

// 1. Native method for processing single images (photo mode)
extern "C" JNIEXPORT jobject JNICALL
Java_com_example_ffddas_MainActivity_processPhotoFrame(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmapInput) {
    
    LOGD("Processing photo frame");
    
    if (bitmapInput == nullptr) {
        LOGE("Input bitmap is null");
        return nullptr;
    }
    
    // Convert bitmap to Mat
    cv::Mat inputMat = bitmapToMat(env, bitmapInput);
    if (inputMat.empty()) {
        LOGE("Failed to convert bitmap to Mat");
        return nullptr;
    }
    
    LOGD("Input Mat size: %dx%d", inputMat.cols, inputMat.rows);
    
    // Process the image (example: convert to grayscale)
    cv::Mat processedMat;
    if (inputMat.channels() == 4) {
        cv::cvtColor(inputMat, processedMat, cv::COLOR_RGBA2GRAY);
        cv::cvtColor(processedMat, processedMat, cv::COLOR_GRAY2RGBA);
    } else if (inputMat.channels() == 3) {
        cv::cvtColor(inputMat, processedMat, cv::COLOR_RGB2GRAY);
        cv::cvtColor(processedMat, processedMat, cv::COLOR_GRAY2RGB);
    } else {
        processedMat = inputMat.clone();
    }
    
    // Create output bitmap
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888",
                                                      "Landroid/graphics/Bitmap$Config;");
    jobject bitmapConfig = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);
    
    jclass bitmapCreateClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapCreateClass, "createBitmap",
                                                             "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    
    jobject outputBitmap = env->CallStaticObjectMethod(bitmapCreateClass, createBitmapMethodID,
                                                       processedMat.cols, processedMat.rows, bitmapConfig);
    
    if (outputBitmap == nullptr) {
        LOGE("Failed to create output bitmap");
        return nullptr;
    }
    
    // Convert processed Mat back to bitmap
    if (!matToBitmap(env, processedMat, outputBitmap)) {
        LOGE("Failed to convert Mat to bitmap");
        return nullptr;
    }
    
    LOGD("Photo frame processed successfully");
    return outputBitmap;
}

// 2. Native method for processing YUV_420_888 camera frames (live mode)
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_ffddas_MainActivity_processPreviewFrame(
        JNIEnv *env,
        jobject /* this */,
        jobject yuvImageBuffer,
        jint width,
        jint height) {
    
    LOGD("Processing preview frame: %dx%d", width, height);
    
    if (yuvImageBuffer == nullptr) {
        LOGE("YUV image buffer is null");
        return nullptr;
    }
    
    // Get direct buffer address
    jbyte *yuvData = static_cast<jbyte *>(env->GetDirectBufferAddress(yuvImageBuffer));
    if (yuvData == nullptr) {
        LOGE("Failed to get YUV data buffer address");
        return nullptr;
    }
    
    jsize yuvDataLength = env->GetDirectBufferCapacity(yuvImageBuffer);
    LOGD("YUV data length: %d", yuvDataLength);
    
    // Convert YUV to RGB
    cv::Mat yuvMat(height + height/2, width, CV_8UC1, (unsigned char*)yuvData);
    cv::Mat rgbMat;
    cv::cvtColor(yuvMat, rgbMat, cv::COLOR_YUV2RGBA_NV21);
    
    // Process the image (example: apply edge detection)
    cv::Mat grayMat;
    cv::cvtColor(rgbMat, grayMat, cv::COLOR_RGBA2GRAY);
    
    cv::Mat edges;
    cv::Canny(grayMat, edges, 50, 150);
    
    cv::Mat resultMat;
    cv::cvtColor(edges, resultMat, cv::COLOR_GRAY2RGBA);
    
    // Convert result to byte array
    jsize resultSize = resultMat.total() * resultMat.elemSize();
    jbyteArray resultArray = env->NewByteArray(resultSize);
    
    if (resultArray == nullptr) {
        LOGE("Failed to create result byte array");
        return nullptr;
    }
    
    env->SetByteArrayRegion(resultArray, 0, resultSize,
                            reinterpret_cast<const jbyte*>(resultMat.data));
    
    LOGD("Preview frame processed successfully");
    return resultArray;
}

// 3. Method for converting Android Bitmap to OpenCV Mat
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_ffddas_MainActivity_bitmapToMat(
        JNIEnv *env,
        jobject /* this */,
        jobject bitmap) {
    
    LOGD("Converting bitmap to Mat");
    
    if (bitmap == nullptr) {
        LOGE("Input bitmap is null");
        return 0;
    }
    
    cv::Mat mat = bitmapToMat(env, bitmap);
    if (mat.empty()) {
        LOGE("Failed to convert bitmap to Mat");
        return 0;
    }
    
    // Store Mat in a smart pointer to manage memory
    std::shared_ptr<cv::Mat> matPtr = std::make_shared<cv::Mat>(mat);
    LOGD("Bitmap converted to Mat successfully: %dx%d", mat.cols, mat.rows);
    return reinterpret_cast<jlong>(new std::shared_ptr<cv::Mat>(matPtr));
}

// 4. Method for converting OpenCV Mat to Android Bitmap
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_ffddas_MainActivity_matToBitmap(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddr,
        jobject outputBitmap) {
    
    LOGD("Converting Mat to bitmap");
    
    if (matAddr == 0) {
        LOGE("Invalid Mat address");
        return JNI_FALSE;
    }
    
    if (outputBitmap == nullptr) {
        LOGE("Output bitmap is null");
        return JNI_FALSE;
    }
    
    std::shared_ptr<cv::Mat> *matPtr = reinterpret_cast<std::shared_ptr<cv::Mat>*>(matAddr);
    cv::Mat &mat = **matPtr;
    
    if (mat.empty()) {
        LOGE("Mat is empty");
        return JNI_FALSE;
    }
    
    bool result = matToBitmap(env, mat, outputBitmap);
    LOGD("Mat converted to bitmap: %s", result ? "success" : "failed");
    return result ? JNI_TRUE : JNI_FALSE;
}

// 5. Canny edge detection implementation with configurable thresholds
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_ffddas_MainActivity_applyCannyDetection(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddr,
        jdouble lowThreshold,
        jdouble highThreshold) {
    
    LOGD("Applying Canny edge detection: low=%f, high=%f", lowThreshold, highThreshold);
    
    if (matAddr == 0) {
        LOGE("Invalid Mat address");
        return 0;
    }
    
    std::shared_ptr<cv::Mat> *inputMatPtr = reinterpret_cast<std::shared_ptr<cv::Mat>*>(matAddr);
    cv::Mat &inputMat = **inputMatPtr;
    
    if (inputMat.empty()) {
        LOGE("Input Mat is empty");
        return 0;
    }
    
    // Convert to grayscale if needed
    cv::Mat grayMat;
    if (inputMat.channels() == 3 || inputMat.channels() == 4) {
        cv::cvtColor(inputMat, grayMat, cv::COLOR_RGBA2GRAY);
    } else {
        grayMat = inputMat;
    }
    
    // Apply Gaussian blur to reduce noise
    cv::Mat blurredMat;
    cv::GaussianBlur(grayMat, blurredMat, cv::Size(5, 5), 1.5);
    
    // Apply Canny edge detection
    cv::Mat edgesMat;
    cv::Canny(blurredMat, edgesMat, lowThreshold, highThreshold);
    
    // Store result in a smart pointer
    std::shared_ptr<cv::Mat> resultPtr = std::make_shared<cv::Mat>(edgesMat);
    LOGD("Canny edge detection applied successfully");
    return reinterpret_cast<jlong>(new std::shared_ptr<cv::Mat>(resultPtr));
}

// 6. Grayscale conversion implementation
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_ffddas_MainActivity_convertToGrayscaleNative(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddr) {
    
    LOGD("Converting Mat to grayscale");
    
    if (matAddr == 0) {
        LOGE("Invalid Mat address");
        return 0;
    }
    
    std::shared_ptr<cv::Mat> *inputMatPtr = reinterpret_cast<std::shared_ptr<cv::Mat>*>(matAddr);
    cv::Mat &inputMat = **inputMatPtr;
    
    if (inputMat.empty()) {
        LOGE("Input Mat is empty");
        return 0;
    }
    
    cv::Mat grayMat;
    if (inputMat.channels() == 3 || inputMat.channels() == 4) {
        cv::cvtColor(inputMat, grayMat, cv::COLOR_RGBA2GRAY);
    } else {
        grayMat = inputMat.clone();
    }
    
    // Store result in a smart pointer
    std::shared_ptr<cv::Mat> resultPtr = std::make_shared<cv::Mat>(grayMat);
    LOGD("Grayscale conversion completed successfully");
    return reinterpret_cast<jlong>(new std::shared_ptr<cv::Mat>(resultPtr));
}

// 7. Memory management for native image buffers
extern "C" JNIEXPORT void JNICALL
Java_com_example_ffddas_MainActivity_releaseMatNative(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddr) {
    
    LOGD("Releasing Mat memory");
    
    if (matAddr == 0) {
        LOGE("Invalid Mat address");
        return;
    }
    
    std::shared_ptr<cv::Mat> *matPtr = reinterpret_cast<std::shared_ptr<cv::Mat>*>(matAddr);
    delete matPtr;
    LOGD("Mat memory released successfully");
}

// Helper: validate odd kernel size >=1
static int ensureOddKernel(int k) {
    if (k < 1) k = 1;
    if (k % 2 == 0) k += 1; // make odd
    return k;
}

// Core pipeline applying blur, canny, morphology; returns RGBA Mat
static cv::Mat runEdgePipeline(const cv::Mat &srcRgba,
                               int gaussianKernel,
                               double sigmaX,
                               double sigmaY,
                               double cannyLow,
                               double cannyHigh,
                               int morphIterations,
                               bool outputGray) {
    cv::Mat working = srcRgba.clone();
    if (working.empty()) {
        LOGE("runEdgePipeline: empty input Mat");
        return cv::Mat();
    }
    // Convert to grayscale
    cv::Mat gray;
    cv::cvtColor(working, gray, cv::COLOR_RGBA2GRAY);

    // Gaussian blur
    gaussianKernel = ensureOddKernel(gaussianKernel);
    try {
        cv::GaussianBlur(gray, gray, cv::Size(gaussianKernel, gaussianKernel), sigmaX, sigmaY);
    } catch (const cv::Exception &e) {
        LOGE("GaussianBlur failed: %s", e.what());
        return cv::Mat();
    }

    // Canny edge detection
    cv::Mat edges;
    try {
        cv::Canny(gray, edges, cannyLow, cannyHigh);
    } catch (const cv::Exception &e) {
        LOGE("Canny failed: %s", e.what());
        return cv::Mat();
    }

    // Morphological post-processing (close + optional dilate/erode)
    if (morphIterations > 0) {
        cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3,3));
        try {
            cv::morphologyEx(edges, edges, cv::MORPH_CLOSE, kernel);
            for (int i = 1; i < morphIterations; ++i) {
                cv::dilate(edges, edges, kernel);
            }
        } catch (const cv::Exception &e) {
            LOGE("Morphology failed: %s", e.what());
        }
    }

    cv::Mat outputRgba;
    if (outputGray) {
        // Return blurred grayscale (optional), or edges as grayscale overlay
        cv::cvtColor(edges, outputRgba, cv::COLOR_GRAY2RGBA);
    } else {
        // For visualization, put edges (white) on transparent background
        cv::Mat edgeMask;
        cv::threshold(edges, edgeMask, 0, 255, cv::THRESH_BINARY);
        outputRgba = cv::Mat(working.rows, working.cols, CV_8UC4, cv::Scalar(0,0,0,0));
        working.copyTo(outputRgba); // base image
        // paint edges in outputRgba as white
        for (int y=0; y<edgeMask.rows; ++y) {
            uchar* em = edgeMask.ptr<uchar>(y);
            cv::Vec4b* out = outputRgba.ptr<cv::Vec4b>(y);
            for (int x=0; x<edgeMask.cols; ++x) {
                if (em[x]) {
                    out[x][0] = 255; // B
                    out[x][1] = 255; // G
                    out[x][2] = 255; // R
                    out[x][3] = 255; // A
                }
            }
        }
    }
    return outputRgba;
}

// 8. Pipeline for RGBA byte array input
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_ffddas_MainActivity_processRgbaBufferPipeline(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray rgbaBytes,
        jint width,
        jint height,
        jint gaussianKernel,
        jdouble sigmaX,
        jdouble sigmaY,
        jdouble cannyLow,
        jdouble cannyHigh,
        jint morphIterations,
        jboolean outputGray) {
    if (rgbaBytes == nullptr) {
        LOGE("processRgbaBufferPipeline: rgbaBytes is null");
        return nullptr;
    }
    jsize len = env->GetArrayLength(rgbaBytes);
    int expected = width * height * 4;
    if (len < expected) {
        LOGE("processRgbaBufferPipeline: buffer too small (%d < %d)", (int)len, expected);
        return nullptr;
    }
    jboolean isCopy = JNI_FALSE;
    jbyte* data = env->GetByteArrayElements(rgbaBytes, &isCopy);
    cv::Mat rgba(height, width, CV_8UC4, (unsigned char*)data);
    cv::Mat output = runEdgePipeline(rgba, gaussianKernel, sigmaX, sigmaY, cannyLow, cannyHigh, morphIterations, outputGray);
    env->ReleaseByteArrayElements(rgbaBytes, data, 0);
    if (output.empty()) {
        LOGE("processRgbaBufferPipeline: output empty");
        return nullptr;
    }
    jsize outSize = output.total() * output.elemSize();
    jbyteArray outArray = env->NewByteArray(outSize);
    env->SetByteArrayRegion(outArray, 0, outSize, (jbyte*)output.data);
    return outArray;
}

// 9. Pipeline for separate YUV_420_888 planes
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_ffddas_MainActivity_processYuvPlanesPipeline(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray yPlane,
        jbyteArray uPlane,
        jbyteArray vPlane,
        jint width,
        jint height,
        jint yRowStride,
        jint uRowStride,
        jint vRowStride,
        jint gaussianKernel,
        jdouble sigmaX,
        jdouble sigmaY,
        jdouble cannyLow,
        jdouble cannyHigh,
        jint morphIterations,
        jboolean outputGray) {
    if (!yPlane || !uPlane || !vPlane) {
        LOGE("processYuvPlanesPipeline: one or more planes null");
        return nullptr;
    }
    int chromaWidth = (width + 1) / 2;
    int chromaHeight = (height + 1) / 2;
    jsize ySize = env->GetArrayLength(yPlane);
    jsize uSize = env->GetArrayLength(uPlane);
    jsize vSize = env->GetArrayLength(vPlane);
    if (ySize < yRowStride * height || uSize < uRowStride * chromaHeight || vSize < vRowStride * chromaHeight) {
        LOGE("processYuvPlanesPipeline: plane sizes insufficient");
        return nullptr;
    }
    jboolean c1=JNI_FALSE,c2=JNI_FALSE,c3=JNI_FALSE;
    jbyte* yPtr = env->GetByteArrayElements(yPlane, &c1);
    jbyte* uPtr = env->GetByteArrayElements(uPlane, &c2);
    jbyte* vPtr = env->GetByteArrayElements(vPlane, &c3);

    // Assemble I420 buffer: Y followed by U then V (each plane contiguous)
    std::vector<uint8_t> i420;
    i420.resize(width * height + 2 * chromaWidth * chromaHeight);
    // Copy Y
    for (int r=0; r<height; ++r) {
        memcpy(&i420[r*width], yPtr + r*yRowStride, width);
    }
    // Copy U
    uint8_t* uDest = &i420[width*height];
    for (int r=0; r<chromaHeight; ++r) {
        memcpy(&uDest[r*chromaWidth], uPtr + r*uRowStride, chromaWidth);
    }
    // Copy V
    uint8_t* vDest = &i420[width*height + chromaWidth*chromaHeight];
    for (int r=0; r<chromaHeight; ++r) {
        memcpy(&vDest[r*chromaWidth], vPtr + r*vRowStride, chromaWidth);
    }

    cv::Mat yuvMat(height + chromaHeight*2, width, CV_8UC1, i420.data()); // (height + height/2) for 420, using chromaHeight*2 ensures correctness with rounding
    cv::Mat rgba;
    try {
        cv::cvtColor(yuvMat, rgba, cv::COLOR_YUV2RGBA_I420);
    } catch (const cv::Exception &e) {
        LOGE("YUV->RGBA conversion failed: %s", e.what());
        rgba.release();
    }

    env->ReleaseByteArrayElements(yPlane, yPtr, 0);
    env->ReleaseByteArrayElements(uPlane, uPtr, 0);
    env->ReleaseByteArrayElements(vPlane, vPtr, 0);

    if (rgba.empty()) {
        LOGE("processYuvPlanesPipeline: RGBA Mat empty after conversion");
        return nullptr;
    }

    cv::Mat output = runEdgePipeline(rgba, gaussianKernel, sigmaX, sigmaY, cannyLow, cannyHigh, morphIterations, outputGray);
    if (output.empty()) {
        LOGE("processYuvPlanesPipeline: output empty");
        return nullptr;
    }
    jsize outSize = output.total() * output.elemSize();
    jbyteArray outArray = env->NewByteArray(outSize);
    env->SetByteArrayRegion(outArray, 0, outSize, (jbyte*)output.data);
    return outArray;
}

// 10. Pipeline on existing Mat (pointer) returning new Mat pointer
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_ffddas_MainActivity_runPipelineOnMat(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddr,
        jint gaussianKernel,
        jdouble sigmaX,
        jdouble sigmaY,
        jdouble cannyLow,
        jdouble cannyHigh,
        jint morphIterations,
        jboolean outputGray) {
    if (matAddr == 0) {
        LOGE("runPipelineOnMat: invalid matAddr");
        return 0;
    }
    auto inputPtr = reinterpret_cast<std::shared_ptr<cv::Mat>*>(matAddr);
    cv::Mat &in = **inputPtr;
    if (in.empty()) {
        LOGE("runPipelineOnMat: input Mat empty");
        return 0;
    }
    cv::Mat rgba;
    if (in.channels() == 4) {
        rgba = in;
    } else if (in.channels() == 3) {
        cv::cvtColor(in, rgba, cv::COLOR_RGB2RGBA);
    } else if (in.channels() == 1) {
        cv::cvtColor(in, rgba, cv::COLOR_GRAY2RGBA);
    } else {
        LOGE("runPipelineOnMat: unsupported channel count %d", in.channels());
        return 0;
    }
    cv::Mat output = runEdgePipeline(rgba, gaussianKernel, sigmaX, sigmaY, cannyLow, cannyHigh, morphIterations, outputGray);
    if (output.empty()) {
        LOGE("runPipelineOnMat: pipeline failed");
        return 0;
    }
    auto resultPtr = std::make_shared<cv::Mat>(output);
    return reinterpret_cast<jlong>(new std::shared_ptr<cv::Mat>(resultPtr));
}

// -------- Glue exports for NativeOpenCVHelper (static methods) ---------
extern "C" JNIEXPORT jobject JNICALL
Java_com_example_ffddas_NativeOpenCVHelper_processPhotoFrame(
        JNIEnv* env, jclass /*clazz*/, jobject bitmapInput) {
    return Java_com_example_ffddas_MainActivity_processPhotoFrame(env, nullptr, bitmapInput);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_ffddas_NativeOpenCVHelper_processPreviewFrame(
        JNIEnv* env, jclass /*clazz*/, jobject yuvImageBuffer, jint width, jint height) {
    return Java_com_example_ffddas_MainActivity_processPreviewFrame(env, nullptr, yuvImageBuffer, width, height);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_ffddas_NativeOpenCVHelper_bitmapToMat(
        JNIEnv* env, jclass /*clazz*/, jobject bitmap) {
    return Java_com_example_ffddas_MainActivity_bitmapToMat(env, nullptr, bitmap);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_ffddas_NativeOpenCVHelper_matToBitmap(
        JNIEnv* env, jclass /*clazz*/, jlong matAddr, jobject outputBitmap) {
    return Java_com_example_ffddas_MainActivity_matToBitmap(env, nullptr, matAddr, outputBitmap);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_ffddas_NativeOpenCVHelper_applyCannyDetection(
        JNIEnv* env, jclass /*clazz*/, jlong matAddr, jdouble lowThreshold, jdouble highThreshold) {
    return Java_com_example_ffddas_MainActivity_applyCannyDetection(env, nullptr, matAddr, lowThreshold, highThreshold);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_ffddas_NativeOpenCVHelper_convertToGrayscaleNative(
        JNIEnv* env, jclass /*clazz*/, jlong matAddr) {
    return Java_com_example_ffddas_MainActivity_convertToGrayscaleNative(env, nullptr, matAddr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_ffddas_NativeOpenCVHelper_releaseMatNative(
        JNIEnv* env, jclass /*clazz*/, jlong matAddr) {
    Java_com_example_ffddas_MainActivity_releaseMatNative(env, nullptr, matAddr);
}
