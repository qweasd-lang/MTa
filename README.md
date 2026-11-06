# MT聊天助手 · 原生版（Kotlin，无 WebView）

100% 原生实现：RecyclerView 消息列表、Material 组件、Dialog 弹窗，**不含任何 WebView / HTML**。

## 功能
- 打字机逐字显示（AI 回复从左到右逐字出现）
- 思考动画（等待回复时呼吸闪烁）
- 会话管理：新建 / 切换 / 删除；每个会话独立请求，切换不会串消息、不会打断
- 模型选择：GLM-4.5 ~ GLM-5.3-Flashx 共 10 个，带能力徽标，**支持搜索 + 全部/仅文字/文字+图片筛选**，选中即时生效
- 设置：温度滑杆 + 三档预设（0.2 / 0.7 / 1.0）
- 发送图片：仅图片，自动压缩到 ≤2400×1080，带预览可取消
- 相对时间：刚刚 / X分钟前 / X小时前 / 昨天 / X天前 / X星期前 / 1个月前 / 之后显示年月日
- 加微信反馈、赞助支持（二维码为本地 PNG，离线可显示）
- 翻译助手（文本）
- 深色主题 + 状态栏同色，适配刘海屏
- API Key 为 XOR+Base64 混淆，源码中无明文

## 构建方式

### 方式一：Android Studio（最简单）
1. Android Studio → `File > Open` → 选择本文件夹
2. 等待 Gradle Sync（首次会自动下载依赖）
3. `Build > Build Bundle(s)/APK(s) > Build APK(s)`
4. 产物：`app/build/outputs/apk/debug/app-debug.apk`

### 方式二：命令行（需 JDK 17 + Android SDK）
```bash
export ANDROID_HOME=/path/to/Android/Sdk
gradle assembleDebug
```

### 方式三：GitHub Actions 自动出包
1. 把本文件夹推到 GitHub 仓库（包含 `.github/workflows/build.yml`）
2. 打开仓库 Actions → `build-apk` → Run workflow
3. 跑完在 Artifacts 里下载 `app-debug-apk`

## 目录结构
```
app/src/main/java/com/mt/chat/
  MainActivity.kt        主界面（聊天、会话、设置、翻译、二维码）
  ChatAdapter.kt         消息列表 + 打字机 + 思考动画
  ModelPickerDialog.kt   可搜索的模型选择器
  Models.kt              模型清单与能力
  TimeFmt.kt             相对时间格式化
  ChatStore.kt           会话本地持久化（JSON）
  ZhipuClient.kt         智谱 GLM 接口（图片按裸 base64 上传）
app/src/main/res/
  layout/                全部界面（纯 XML 原生布局）
  drawable-nodpi/        wxqr.png、sponsor.png（本地二维码）
```

## 说明
- 包名 `com.mt.chat.nativeai`，与网页版包名不同，可共存安装
- 网络：`https://open.bigmodel.cn/api/paas/v4/chat/completions`
- 若要换 Key：修改 `ZhipuClient.kt` 里的 `ENC`（保持同样的混淆方式）
