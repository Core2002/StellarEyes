package `fun`.fifu.stellareyes

import android.content.res.AssetManager
import android.graphics.Bitmap

object NcnnController {

    // 加载你的 JNI 库
    init {
        System.loadLibrary("stellar_eyes_native") // 替换为你在 CMakeLists.txt 中定义的库名称
    }

    // NCNN 初始化方法，将 AssetManager 传递给 C++
    private external fun initNcnn(assetManager: AssetManager): Boolean

    // NCNN 推理方法，传递 Bitmap 输入，返回 float 数组输出
    external fun runInference(bitmap: Bitmap): FloatArray

    // 余弦相似度
    external fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float


    // 对外封装调用方法
    fun setupNcnn(assetManager: AssetManager): Boolean {
        return initNcnn(assetManager)
    }

    fun performInference(imageBitmap: Bitmap): FloatArray {
        return runInference(imageBitmap)
    }
}
