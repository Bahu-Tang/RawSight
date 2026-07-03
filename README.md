# RawSight — Pre-capture Computational Photography Assistant

> **一个"拍摄前决策增强系统"，帮助用户在按下快门前获得最佳构图与曝光决策，并尽可能保留真实光学数据。**

---

## 项目概述

RawSight 是一个 Android Demo 应用。目标不是滤镜或后期，而是在拍照**之前**用 AI + 传感器数据分析当前场景，实时给出构图建议、曝光建议、抖动警告。绝不修改图像数据（No post-processing, no filters）。

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 相机 | Camera2 API（非 CameraX） |
| 最低 SDK | Android 10 (API 29) |
| 目标 SDK | Android 14 (API 34) |
| 项目结构 | 单 module，按 package 分层 |
| 构建 | Gradle 8.5 + AGP 8.2 |

## 模块架构

```
com.rawsight/
├── MainActivity.kt                # 单 Activity，总集成
├── camera/
│   ├── CameraParams.kt            # ISO/Shutter/WB/Focus/EV 枚举 + 预设值
│   ├── CameraService.kt           # Camera2 生命周期、预览流、参数控制
│   ├── CaptureController.kt       # JPEG + DNG 拍照 (单 session 双格式)
│   └── LensManager.kt             # 多镜头发现与切换 (前/后, 去重)
├── ai/
│   ├── AIAnalyzer.kt              # AI 分析接口
│   ├── AIAnalysisResult.kt        # 场景/主体/曝光/构图 数据类
│   └── StubAIAnalyzer.kt          # 模拟 AI (≤2 FPS, 随机场景检测)
├── decision/
│   ├── DecisionEngine.kt          # 融合 AI + IMU + 相机元数据 → 状态判定
│   └── ShotReadinessState.kt      # 拍摄就绪状态 (OPTIMAL/WARNING/CRITICAL)
├── imu/
│   ├── IMUData.kt                 # 倾斜角 + 抖动等级
│   └── IMUService.kt              # SensorManager (重力+陀螺仪, ~10Hz)
├── settings/
│   └── SettingsEngine.kt          # 每参数 AUTO/MANUAL 独立状态
├── storage/
│   └── FileSaver.kt               # 文件保存 + MediaStore 注册
└── ui/
    ├── CameraScreen.kt            # 主预览界面 (TextureView + 分层 Overlay)
    ├── OverlayRenderer.kt         # Canvas: 网格/检测框/箭头/水平仪
    ├── TopHUD.kt                  # 顶部状态条 (Green/Yellow/Red)
    ├── ShutterButton.kt           # 快门按钮 + 脉冲环
    ├── BottomSheetPanel.kt        # 完整参数面板 (展开式)
    └── theme/Theme.kt             # 暗色主题
```

## 功能清单

### 相机核心
- [x] Camera2 30FPS 预览
- [x] JPEG 拍照 + Gallery 可见
- [x] RAW DNG 拍照（设备支持时）
- [x] 手动控制: ISO / 快门 / 白平衡 / 对焦 / EV 补偿
- [x] 每个参数独立 AUTO / MANUAL
- [x] 多镜头切换（后置多摄 + 前置）
- [x] 镜头去重（过滤 LOGICAL_MULTI_CAMERA）
- [x] 预览宽高比校正（CENTER_CROP 矩阵）

### AI 分析引擎
- [x] AI 接口预定（AIAnalyzer + TFLite 接口）
- [x] Stub 模拟（随机场景/主体/曝光/构图结果，≤2 FPS）
- [ ] 真实场景检测模型集成

### 决策引擎
- [x] 融合 AI + IMU + 相机元数据
- [x] 五种就绪状态: OPTIMAL / SUBOPTIMAL_COMPOSITION / LIGHTING_WARNING / HIGH_MOTION_RISK / CRITICAL_RISK
- [x] 顶部 HUD 实时显示状态 + 颜色

### UI/UX
- [x] 三分法构图网格 Canvas Overlay
- [x] 主体检测框（白色细线）
- [x] 构图建议箭头（冷蓝色脉冲）
- [x] IMU 水平仪（旋转横线 + 居中绿点）
- [x] 快门按钮 + 就绪状态环 + 防连击
- [x] 底部离散滑块参数控制（ISO/SPD/WB/FOC/EV）
- [x] 完整参数面板（BottomSheet，点 `...` 展开）
- [x] 中英双语提示
- [x] 暗色主题

### 传感器
- [x] IMU 抖动/倾斜检测 (SensorManager)
- [x] 倾斜角 (重力传感器, ~10Hz)
- [x] 抖动等级 (陀螺仪滑动窗口)

## 版本历史

| 版本 | 日期 | 主要内容 |
|------|------|---------|
| v1.0.0 | 2026-07-02 | 初始版本：完整项目骨架、Camera2 预览+拍照、AI Stub、DecisionEngine、UI 系统 |
| v1.0.1 | 2026-07-03 | 预览缩放修复、前置摄像头、滑块参数控制、镜头去重、APK 版本命名 |
| v1.0.2 | 2026-07-03 | 离散档位滑块、对焦外移、白平衡生效、EV 限制说明、预览微调 |

## 已知限制

- EV 补偿仅在自动曝光（ISO+快门均为 AUTO）下生效（Camera2 硬件限制）
- AI 分析当前为 Stub 模拟，实际场景检测待集成
- 多镜头切换后预览需重新初始化，暂无明显动效
- 模拟器 Camera2 支持不完整，建议真机测试

## 构建

```bash
# 环境要求：Java 17+, Android SDK 34, Gradle 8.5
export ANDROID_HOME=F:/Android
export JAVA_HOME=F:/androidstudio/jbr
./gradlew assembleDebug
```

## Git

```bash
git remote add origin https://github.com/<user>/<repo>.git
git push -u origin main
```
