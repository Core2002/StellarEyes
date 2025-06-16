#include <jni.h>
#include <string>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <vector>
#include <cmath> // For std::sqrt and pow (std::sqrt is used)
#include <numeric> // For std::inner_product (not used in final optimized version but often included)
#include "./ncnn-20250503-android-vulkan-shared/arm64-v8a/include/ncnn/net.h" // NCNN 的网络头文件

#define TAG "StellareEyes_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static ncnn::Net net; // 全局网络对象

extern "C" JNIEXPORT jboolean JNICALL
Java_fun_fifu_stellareyes_NcnnController_initNcnn(JNIEnv *env, jobject thiz, jobject assetManager) {
    LOGD("Running initNcnn...");
    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("Failed to get AAssetManager");
        return JNI_FALSE;
    } else {
        LOGD("AAssetManager obtained successfully!");
    }

    net.opt.use_vulkan_compute = true; // 尝试启用 GPU 加速 (如果设备支持)
    net.opt.num_threads = 4; // 设置推理线程数

    // 从 assets 加载模型
    LOGD("Loading NCNN model param...");
    int ret_param = net.load_param(mgr, "mbv2facenet.param"); // 替换为你的模型文件名
    LOGD("Loading NCNN model...");
    int ret_bin = net.load_model(mgr, "mbv2facenet.bin"); // 替换为你的模型文件名
//    int ret_param = net.load_param(mgr, "facenet_512.tflite.onnx.ncnn.param"); // 替换为你的模型文件名
//    int ret_bin = net.load_model(mgr, "facenet_512.tflite.onnx.ncnn.bin"); // 替换为你的模型文件名

    if (ret_param != 0 || ret_bin != 0) {
        LOGE("Failed to load NCNN model: param_ret=%d, bin_ret=%d", ret_param, ret_bin);
        return JNI_FALSE;
    }

    LOGD("NCNN model loaded successfully!");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_fun_fifu_stellareyes_NcnnController_runInference(JNIEnv *env, jobject thiz, jobject bitmap) {
    LOGD("Running inference...");
    // 假设输入是一个 Bitmap，你需要将其转换为 ncnn::Mat
    // 这是一个复杂的步骤，涉及到 Bitmap 到像素数据的转换，并可能进行预处理 (缩放，归一化等)
    // 简化示例，假设你已经有了预处理好的 ncnn::Mat input_mat;

    // TODO: 将 Android Bitmap 转换为 ncnn::Mat 并进行预处理
    // 通常需要获取 Bitmap 像素，然后填充 ncnn::Mat
    // 例如：
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    void *pixels;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    ncnn::Mat in = ncnn::Mat::from_pixels_resize(static_cast<const unsigned char *>(pixels),
                                                 ncnn::Mat::PIXEL_RGBA2RGB, info.width, info.height,
                                                 160, 160);
    AndroidBitmap_unlockPixels(env, bitmap);

    // 假设你已经有了预处理好的 ncnn::Mat input_mat
    // 例如，一个简单的测试输入
//    ncnn::Mat input_mat(160, 160, 3);
//    input_mat.range(0.0f, 127.0f); // 填充一些虚拟数据

    // 创建推理提取器
    ncnn::Extractor ex = net.create_extractor();
    ex.set_light_mode(true); // 轻量模式，减少内存和CPU使用
    ex.input("data", in); // 模型输入层名称
//    ex.input("pnnx_input_0", input_mat); // 模型输入层名称

    // 获取输出
    ncnn::Mat out;
    ex.extract("fc1", out); // 模型输出层名称
//    ex.extract("pnnx_output_0", out); // 模型输出层名称

    // 将 ncnn::Mat 输出转换为 Java float array
    jfloatArray resultArray = env->NewFloatArray(out.w); // 假设输出是一个一维向量
    env->SetFloatArrayRegion(resultArray, 0, out.w, reinterpret_cast<const jfloat *>(out.data));
    LOGD("Inference completed!");
    return resultArray;
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_fun_fifu_stellareyes_NcnnController_cosineSimilarity(
        JNIEnv *env,
        jobject /* this */,
        jfloatArray vec1_java,
        jfloatArray vec2_java) {

    jsize length1 = env->GetArrayLength(vec1_java);
    jsize length2 = env->GetArrayLength(vec2_java);

    // 长度不一致或长度为0，则返回 0
    if (length1 != length2 || length1 == 0) {
        return 0.0f;
    }

    // 使用 GetPrimitiveArrayCritical 以可能避免数组复制，从而提高性能
    // 注意：在 GetPrimitiveArrayCritical 和 ReleasePrimitiveArrayCritical 之间不应进行其他 JNI 调用或阻塞操作
    const auto *vec1_ptr = static_cast<const jfloat*>(env->GetPrimitiveArrayCritical(vec1_java, nullptr));
    if (vec1_ptr == nullptr) {
        // 获取数组元素失败 (例如，内存不足)
        return 0.0f;
    }

    const auto *vec2_ptr = static_cast<const jfloat*>(env->GetPrimitiveArrayCritical(vec2_java, nullptr));
    if (vec2_ptr == nullptr) {
        // 获取数组元素失败，释放第一个数组的引用
        env->ReleasePrimitiveArrayCritical(vec1_java, const_cast<jfloat*>(vec1_ptr), JNI_ABORT);
        return 0.0f;
    }

    // 使用 double 进行中间累加，以提高数值精度
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (jsize i = 0; i < length1; ++i) {
        // 将乘积中的一个操作数提升为 double，以确保乘积以 double 类型计算
        dotProduct += static_cast<double>(vec1_ptr[i]) * vec2_ptr[i];
        normA += static_cast<double>(vec1_ptr[i]) * vec1_ptr[i];
        normB += static_cast<double>(vec2_ptr[i]) * vec2_ptr[i];
    }

    // 尽快释放数组引用
    // 使用 JNI_ABORT 是因为我们没有修改数组内容，不需要将更改写回 Java 堆
    env->ReleasePrimitiveArrayCritical(vec2_java, const_cast<jfloat*>(vec2_ptr), JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(vec1_java, const_cast<jfloat*>(vec1_ptr), JNI_ABORT);

    // 检查范数是否为零，以避免除以零
    if (normA == 0.0 || normB == 0.0) {
        return 0.0f;
    }

    // 计算余弦相似度
    auto similarity = static_cast<float>(dotProduct / (std::sqrt(normA) * std::sqrt(normB)));

    return similarity;
}
