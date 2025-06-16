# StellarEyes

## 简介

StellarEyes 是一个 Android 应用程序，利用设备端机器学习 (ML) 技术，通过设备的摄像头实时进行人脸检测和人脸识别。它能够从摄像头捕捉图像，检测图像中的人脸，提取人脸特征（embeddings），并将这些特征与已存储的人脸数据库进行比对，以识别个体。用户还可以管理已存储的人脸数据。

## 核心功能

* **实时人脸检测**: 使用 Google ML Kit 从摄像头预览中实时检测人脸。
* **人脸特征提取**: 使用 TensorFlow Lite (FaceNet 模型) 从检测到的人脸图像中提取高维特征向量 (embeddings)。
* **人脸识别与匹配**:
  * 使用 NCNN 进行高效的余弦相似度计算，比对新提取的人脸特征与数据库中已存储的特征。
  * 识别已知人脸或提示为新用户。
* **人脸数据管理**:
  * 存储和管理人脸特征、关联的图像及名称。
  * 提供查看、重命名和删除已存储人脸的功能。
  * 数据通过 `VectorSearchEngine` 存储在本地 JSON 文件 (`vectors.json`) 中。
* **可配置的识别行为**:
  * **持续推理**: 可选择是否持续分析摄像头画面中的人脸。
  * **处理所有/最大人脸**: 可选择处理检测到的所有人脸，或仅处理画面中最大的人脸。
  * **自动录入**: 可选择是否自动将符合条件的新人脸添加到数据库。
* **摄像头控制**: 支持前后摄像头切换。
* **UI**: 基于 Jetpack Compose 构建的现代化用户界面，支持深色主题。

## 主要技术栈

* **Kotlin**: 作为主要开发语言，充分利用了协程 (Coroutines) 进行异步处理。
* **Jetpack Compose**: 用于构建声明式用户界面。
* **CameraX**: 用于简化摄像头访问和预览。
* **Google ML Kit**:
  * **Face Detection**: 用于在图像中快速准确地检测人脸。
* **TensorFlow Lite (TFLite)**:
  * 使用预训练的 `facenet_512.tflite` 模型生成 512 维的人脸特征向量。
  * 支持 GPU 代理以加速推理。
* **NCNN**: 腾讯开源的高性能神经网络推理框架。
  * 通过 JNI 调用 C++ 实现，用于初始化 NCNN 环境、运行模型（可能指除了FaceNet之外的模型，或NCNN实现的算子）以及计算特征向量间的余弦相似度。
* **Android NDK (C++)**: `main.cpp` 和 `stellar_eyes_native` 库表明了原生代码的集成，主要用于 NCNN 的相关操作。
* **DataStore**: 用于持久化存储应用设置 (如持续推理开关、深色主题等)。
* **Kotlinx Serialization**: 用于将 `VectorSearchEngine` 中的人脸数据序列化为 JSON 格式进行存储和加载。
* **Material Design 3**: 应用的视觉风格和组件库。
* **ViewModel**: 用于管理 UI 相关数据并处理业务逻辑。
* **Navigation Compose**: 用于在不同的 Composable 屏幕间导航。

## 工作流程简述

1. **权限请求**: 应用启动时检查并请求摄像头权限。
2. **摄像头预览**: 使用 CameraX 显示实时摄像头画面。
3. **NCNN 初始化**: 在后台协程中初始化 NCNN 环境。
4. **FaceNet 初始化**: 在专用协程（`TFLiteThread`）中初始化 FaceNet TFLite 模型，可选择启用 GPU 代理。
5. **人脸检测 (ML Kit)**: `CameraPreview` 对每一帧图像进行分析，使用 ML Kit FaceDetector 检测人脸。
6. **绘制边界框**: `FaceBoundingBoxOverlay` 在检测到的人脸周围绘制红色矩形框。
7. **图像捕捉与处理**:
    * 根据用户设置（持续推理或手动触发），捕捉当前帧或特定图像。
    * 从捕捉到的图像中裁剪出人脸区域 (支持处理单个最大人脸或所有人脸)。
    * 裁剪后的人脸图像被缩放到 FaceNet 模型所需的输入尺寸 (160x160)。
8. **特征提取 (FaceNet)**:
    * 预处理后的人脸图像输入到 FaceNet 模型中，生成 512 维的特征向量。
9. **人脸识别/录入 (VectorSearchEngine & NCNN)**:
    * `FaceRecognitionViewModel` 调用 `VectorSearchEngine`。
    * `VectorSearchEngine` 使用 NCNN 提供的余弦相似度计算，将新提取的特征向量与数据库中存储的所有向量进行比较。
    * 如果找到足够相似的匹配项 (相似度 > 0.84f)，则识别为已知用户。
    * 如果没有匹配项且启用了自动录入，并且满足特定条件（如连续帧检测到同一未知人脸），则将新的人脸特征、图像（Base64编码）和自动生成的名称存入数据库。
10. **结果展示**: 在 UI 上显示识别结果（匹配到的用户、相似度）或新用户已添加的提示。
11. **数据管理**: 用户可以通过 "管理数据" 界面查看、重命名或删除已存储的人脸条目。所有更改都会保存到 `vectors.json`。

## 项目模块

* **StellarEyes.app**: 核心 Android 应用模块。
  * `MainActivity.kt`: 应用主入口，处理权限和导航。
  * `NcnnController.kt`: NCNN JNI 接口。
  * `FaceNet.kt`: FaceNet TFLite 模型加载与推理。
  * `VectorSearchEngine.kt`: 人脸特征向量数据库及搜索逻辑。
  * `ui/camera/`: 包含摄像头预览、人脸检测、识别相关的 UI 和 ViewModel。
  * `ui/managefaces/`: 包含管理人脸数据的 UI 和 ViewModel。
  * `ui/settings/`: 包含设置和关于页面的 UI 和 ViewModel。
  * `data/`: 数据模型和存储相关。
* (其他标准模块如 .androidTest, .unitTest, buildSrc 等)

## 如何编译和运行

1. **克隆项目**:

    ```bash
    git clone [项目GIT仓库地址]
    cd StellarEyes
    ```

2. **Android Studio**:
    * 使用 Android Studio (建议最新稳定版) 打开项目。
    * 等待 Gradle 同步完成。
    * 确保已安装 Android NDK 以编译 C++ 代码 (NCNN JNI 部分)。
3. **模型文件**:
    * 确保 `facenet_512.tflite` 模型文件位于 `app/src/main/assets/` 目录下。
    * NCNN 模型文件（如果 NCNN JNI `initNcnn` 方法需要）也应放置在 `assets` 中并由 C++ 代码正确加载。
4. **构建和运行**:
    * 连接 Android 设备或启动模拟器 (API 级别适配见 `build.gradle.kts`)。
    * 点击 "Run" 按钮。

## 待办事项 / 未来改进 (可选)

* [可以添加一些未来可能实现的功能]
* 优化模型加载和初次运行的性能。
* 提供更详细的错误处理和用户反馈。

## 许可证

[请在此处填写项目的许可证信息，例如 MIT, Apache 2.0 等]
