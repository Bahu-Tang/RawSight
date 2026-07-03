🚀 RawSight — opencode 工程版 Prompt（UI完整系统版）
🧠 ROLE

你是一个 Android 高级计算摄影系统工程师，需要实现一个名为 RawSight 的 Demo 应用。

RawSight 的目标不是滤镜或后期，而是：

一个“拍摄前决策增强系统”，帮助用户在按下快门前获得最佳构图与曝光决策，并尽可能保留真实光学数据（RAW优先）。

🎯 CORE PRODUCT DEFINITION

RawSight is a:

Pre-capture computational photography assistant system.

It must:

NEVER modify captured images
NEVER apply filters or beautification
ONLY assist decision-making before capture
📦 FUNCTIONAL REQUIREMENTS
1. 📷 CAMERA SYSTEM（核心）

使用 Camera2 或 CameraX 实现单一 Camera session。

必须支持：
预览流（30 FPS）
拍照（JPEG）
RAW capture（DNG，如果设备支持）
手动控制参数：
ISO：64–6400
Shutter：1/8000s – 30s
White Balance：2500K–7500K
Manual Focus
EV compensation
AUTO / MANUAL 独立切换机制：

每个参数必须支持：

AUTO
MANUAL

且互不影响。

2. 🧠 AI ANALYSIS ENGINE（轻量）
输入：
低分辨率 preview frame（downsampled）
最大 2 FPS
输出：
data class AIAnalysisResult(
    val sceneType: String, // portrait / landscape / night / macro
    val subjectBox: RectF?,
    val exposureSuggestion: ExposureAdvice,
    val compositionSuggestion: CompositionAdvice
)
compositionSuggestion：
moveLeft / moveRight / moveUp / moveDown
tilt correction hint
framing quality score (0–1)
限制：
必须异步执行
不允许阻塞 Camera thread
不允许 >2 FPS inference
3. 🎯 DECISION ENGINE（核心逻辑）

该模块负责“是否适合拍摄”的判断：

输出状态：
OPTIMAL_SHOT_READY (green)
SUBOPTIMAL_COMPOSITION (yellow)
LIGHTING_WARNING (yellow)
CRITICAL_RISK (red)
判断依据：
曝光是否偏离最佳范围
主体是否居中或符合构图规则
是否存在明显抖动（IMU）
对焦是否稳定
4. 🎨 UI SYSTEM（RawSight视觉系统）
📱 4.1 主预览界面（Camera Screen）

必须包含：

Overlay Elements：
① 构图辅助线
三分法 grid
极低透明度（10–20%）
② 主体检测框
AI识别目标 bounding box
白色细线框
③ 构图建议箭头
← → ↑ ↓
半透明冷蓝色
④ 水平仪（IMU）
中心横线
偏移时轻微倾斜动画
📊 4.2 参数控制面板（Bottom Sheet）

必须实现：

显示格式：
ISO     100      AUTO
SPEED   1/250    MANUAL
WB      5200K    AUTO
FOCUS   MF       MANUAL
EV      +0.0     AUTO
UI规则：
等宽字体（Monospace）
AUTO / MANUAL toggle
数值不可复杂滑动（Demo简化）
⚡ 4.3 拍摄决策提示条（Top HUD）

显示：

状态之一：
"OPTIMAL SHOT READY"
"SUBOPTIMAL COMPOSITION"
"LOW LIGHT WARNING"
"HIGH MOTION RISK"
UI样式：
背景透明黑
状态文字 + 小色条

颜色规则：

Green = optimal
Yellow = warning
Red = risk
📸 4.4 快门按钮区
中央快门按钮：
白色圆形
外圈状态环：
状态	颜色
Optimal	Green glow
Warning	Yellow pulse
Risk	Red pulse
🧱 ARCHITECTURE

必须严格模块化：

1. CameraService
Camera lifecycle
Lens switching (0.6x / 1x / 3x)
Parameter control
2. CaptureController
JPEG capture
RAW capture
File saving
3. AIAnalyzer
Frame sampling (≤2 FPS)
Scene detection
Composition analysis
4. DecisionEngine
Merge AI + camera metadata + IMU
Output shot readiness state
5. UIOverlayController
Grid rendering
Bounding box
Arrow guidance
Level indicator
6. SettingsEngine
AUTO / MANUAL state per parameter
⚙️ PERFORMANCE CONSTRAINTS
Preview ≥ 30 FPS
AI ≤ 2 FPS
No camera thread blocking
Memory ≤ 600MB
Thermal safe fallback:
reduce AI to 1 FPS when hot
❌ STRICTLY FORBIDDEN
❌ ARCore / SLAM / 3D reconstruction
❌ image filtering or beautification
❌ multi camera session
❌ AI controlling camera directly
❌ blocking inference on main thread
❌ high frequency ML (>2 FPS)
📦 OUTPUT REQUIREMENTS

Please generate:

Android Studio project structure (Kotlin)
Camera2 / CameraX implementation
RAW + JPEG capture logic
AIAnalyzer stub + TFLite integration interface
DecisionEngine implementation
UI Overlay system (Canvas-based)
BottomSheet parameter panel UI
MainActivity integration
🎯 FINAL PRODUCT GOAL

RawSight Demo must behave as:

A professional-grade camera interface where AI assists the photographer before capture, without modifying any image data.

🚀 OPTIONAL EXTENSIONS (DO NOT IMPLEMENT YET)
ARCore integration
NPU acceleration tuning
Advanced depth estimation
Multi-frame HDR fusion