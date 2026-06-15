# 星星打字通 (XX-Typing)

[![Build](https://github.com/cyrixvvv/typing-app/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/cyrixvvv/typing-app/actions/workflows)

> 面向 TV/机顶盒 的打字练习应用，支持英语指法训练与拼音中文输入训练。

## 概览

星星打字通是一款运行在 Android TV 平台上的打字练习应用。通过 OTG 物理键盘或 TV 遥控器操作，提供初级指法训练（英语/拼音）和高级模式测试（WPM/CPM 统计），内置按键音效、超时提醒、得分系统和练习记录。

**关键特性：**
- **双模式**：初级模式（指法训练 + 可视化键盘）与高级模式（WPM/CPM 速度测试）
- **拼音输入**：内置轻量拼音字典，支持全拼输入与选字
- **TV 适配**：基于 LeanBack 库，横屏设计，支持遥控器 D-pad 导航与 OTG 键盘
- **数据记录**：Room 数据库持久化练习成绩，追踪 WPM/CPM/正确率/得分
- **即时反馈**：按键音效 + 超时闪烁动画 + 鼓励语

## 技术栈

| 层级 | 技术 |
|---|---|
| 语言 | Kotlin (JVM 1.8) |
| UI | AndroidX LeanBack, ViewBinding |
| 架构 | AppCompatActivity + MVVM (ViewModel + LiveData) |
| 数据库 | Room 2.5.2 |
| 构建 | Gradle 7.4.2, compileSdk 33, minSdk 22 |

## 项目结构

```
typing-app/
├── build.gradle                      # 顶层构建（kotlin 1.8.22, 各库版本）
├── settings.gradle                   # 项目名: HongluTyping
└── app/
    ├── build.gradle                  # 依赖: leanback, room, lifecycle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml        # LeanBack 应用, 4 个 Activity
        ├── assets/
        │   ├── contents_cn.json       # 中文练习文本
        │   ├── contents_en.json       # 英文练习文本
        │   └── pinyin_dict.json       # 拼音字典 (pinyin → [字符, 频率])
        ├── java/com/honglu/typing/
        │   ├── MainActivity.kt            # 主菜单 → 初级/高级/内容选择/设置
        │   ├── TypingScreenActivity.kt    # 抽象基类: 键盘事件分发/显示更新/计时/评分
        │   ├── engine/
        │   │   ├── TypingEngine.kt        # 核心引擎: 按键匹配/WPM/CPM/准确率
        │   │   ├── ScoreManager.kt        # 评分计算
        │   │   └── SoundManager.kt        # 按键音效 (correct/wrong/click)
        │   ├── input/
        │   │   ├── InputMode.kt           # 状态机: English / Pinyin / PinyinSelecting
        │   │   └── PinyinInputEngine.kt   # 拼音引擎: 字典加载 + 候选字检索
        │   ├── main/
        │   │   ├── PrimaryModeActivity.kt     # 初级模式: 指法训练 + 拼音
        │   │   ├── AdvancedModeActivity.kt     # 高级模式: WPM 速度测试
        │   │   ├── ContentSelectActivity.kt   # 内容选择界面 (CardPresenter)
        │   │   ├── SettingsActivity.kt        # 设置: 音效/超时时间/清空数据
        │   │   └── CardPresenter.kt           # LeanBack 卡片展示器
        │   ├── data/
        │   │   ├── AppDatabase.kt             # Room 数据库单例
        │   │   ├── RecordEntity.kt            # 练习记录实体
        │   │   └── ContentRepository.kt       # 随机文本抽取
        │   ├── ui/
        │   │   └── KeyboardView.kt            # 自定义虚拟键盘视图
        │   └── util/
        │       ├── DeviceUtils.kt             # 键盘/遥控器事件识别
        │       └── TimeUtils.kt               # 时间工具
        └── res/
            ├── layout/                        # 5 个布局文件
            ├── values/                        # 颜色/尺寸/字符串/主题
            ├── raw/                           # 3 个音效文件
            └── ...
```

## 核心设计

### TypingScreenActivity — 双模式基类

所有打字界面继承 `TypingScreenActivity`，统一处理：

1. **事件分发**：物理键盘（OTG）→ 遥控器 D-pad → 子类拼音处理
2. **显示更新**：进度标记 `【char】`、未输入显示 `_`、高级模式保留原文
3. **超时提醒**：5 秒无操作键盘闪烁动画（可设置）
4. **评分系统**：准确率 (40%) + 速度 (40%) + 连续正确奖励 (20%)
5. **结果持久化**：完成后自动写入 Room 数据库

### TypingEngine — 纯逻辑核心

无 UI 依赖的核心打字引擎，负责：
- 按键匹配（不区分大小写）
- WPM 计算（1 word = 5 chars）
- CPM 计算（中文专用，1 char = 1 unit）
- 准确率统计与连续正确计数

### 拼音输入流程

```
a-z 字母键 → 拼音累加 → 匹配字典 → D-pad/数字键选字 → Enter 确认 → 写入文本
```

## 构建

### GitHub Actions（唯一推荐方式）

**自动构建：** 每次推送到 `master` 分支自动构建 Release APK（armeabi-v7a + arm64-v8a）

**手动触发：** GitHub Actions 页面 → `build.yml` → `Run workflow`

**无缓存强制重建：** 触发时选择 `Clear artifacts and caches` 或在 workflow 中使用 `--no-build-cache --rerun-tasks`

#### 下载 APK

构建产物在 **Actions → Artifacts**，每个 APK 保留 7 天。

```
https://github.com/cyrixvvv/typing-app/actions
→ 点击最新构建
→ 底部 Artifacts
→ 下载 app-release-armeabi-v7a.apk 或 app-release-arm64-v8a.apk
```

### 本地构建

需要 Android SDK (compileSdk 33) + JDK 17：

```bash
cd typing-app
./gradlew assembleDebug   # Debug 包
./gradlew assembleRelease # Release 包 (启用 ProGuard)
```

### 依赖清单

| 依赖 | 版本 | 用途 |
|---|---|---|
| kotlin | 1.8.22 | 编程语言 |
| androidx.leanback | 1.0.0 | TV 界面 |
| androidx.room | 2.5.2 | 本地数据库 |
| androidx.constraintlayout | 2.1.4 | 布局 |
| androidx.core-ktx | 1.10.1 | AndroidX 扩展 |
| androidx.appcompat | 1.6.1 | 兼容性 |
| lifecycle | 2.6.2 | ViewModel + LiveData |

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| timeout_seconds | 5 | 超时提醒触发秒数 |
| sound_enabled | true | 按键音效开关 |

存储位置：`typing_config` SharedPreferences

---

## GitHub Actions 构建实战手册

### 常用操作

#### 查询最新构建状态

```bash
# 查看最近 5 次构建
gh run list --workflow=build.yml -L 5

# 查看实时日志（跟随输出）
gh run watch <run-id>

# 查看具体构建的通过/失败状态
gh run view <run-id> --log-failed
```

#### 下载构建产物

```bash
# 列出最新构建的 artifact
gh run view <run-id> --json artifacts

# 下载所有 artifacts
gh run download <run-id>

# 下载指定 artifact
gh run download <run-id> -n app-release-arm64-v8a
```

#### 触发重新构建

```bash
# 通过 gh CLI 触发 workflow_dispatch
gh workflow run build.yml

# 触发并等待结果
gh workflow run build.yml --ref master && gh run watch
```

#### 无缓存强制重建

当代码修改已推送但构建仍报旧的编译错误（Gradle 缓存污染）时：

**方法 1：Workflow 内置清理（当前配置）**
```bash
# workflow 中已配置: ./gradlew clean + 清除 build-cache + touch 所有源文件
# 推送后自动触发时会执行清理步骤
```

**方法 2：通过 GitHub Web 界面**
```
Actions → build.yml → 运行记录 → 右上角 "Clear cache" → 重新 Run workflow
```

**方法 3：通过 API 清除缓存**
```bash
# 查找所有 Gradle 缓存
gh api repos/cyrixvvv/typing-app/actions/caches \
  --method GET --jq '.actions_caches[] | select(.key | contains("gradle")) | {id, key}'

# 删除指定缓存
gh api repos/cyrixvvv/typing-app/actions/caches/<cache-id> --method DELETE
```

#### 查询构建日志

```bash
# 查看构建失败的具体步骤
gh run view <run-id> --log-failed | head -100

# 下载完整日志
gh api repos/cyrixvvv/typing-app/actions/runs/<run-id>/logs > logs.zip

# 查看特定 step 的日志
gh api repos/cyrixvvv/typing-app/actions/runs/<run-id>/jobs --jq '.jobs[0].steps'
```

### 调试编译错误

#### 典型编译错误与修复

**1. 缺失 import**
```
Unresolved reference: AdvancedModeActivity
```
→ 在文件顶部添加 `import com.honglu.typing.main.AdvancedModeActivity`

**2. KeyEvent API 兼容性**
```
Unresolved reference: META_SHIFT_LEFT
```
→ `KeyEvent.META_SHIFT_LEFT` 在旧 API 不可用，改用 `KeyEvent.META_SHIFT_MASK`

**3. 私有 setter（与 Java 互操作）**
```
Visibility modifier expected
```
→ `var score: Int = 0 private set` 改为 `internal set`

**4. lambda 返回类型推断**
```
Type mismatch: inferred type is Boolean, expected Unit
```
→ `runOnUiThread { ... }` 中的 lambda 不能依赖最后一行类型，显式用 `runOnUiThread(Runnable { ... })`

**5. 跨模块访问权限**
```
Var cannot be accessed from subclass in different package
```
→ `internal` 或 `protected open` 修饰属性

**6. suspend 函数在非协程上下文调用**
```
Suspend function 'calculateScore' should be called only within a coroutine
```
→ 用 `lifecycleScope.launch { scoreManager.calculateScore(...) }` 包裹

**7. Gradle 缓存污染（最常见！）**
```
Unresolved reference: source  (但 DeviceUtils.kt 中明明有 device.source())
```
→ 这是缓存残留，不是源码问题。执行以下之一：
   - 推送 `--allow-empty` 触发全新构建
   - 清除 GitHub Actions 缓存
   - Workflow 中 `./gradlew clean` + `rm -rf .gradle`

#### 调试流程

```
1. gh run view <run-id> --log-failed    # 找到第一个错误
2. 定位错误文件和行号
3. 本地修改并 push
4. 观察新构建（无缓存模式下无需清除缓存）
5. 重复直到通过
```

### workflow.yml 关键配置

```yaml
- name: Clear stale build artifacts        # 防止缓存污染
  run: |
    ./gradlew clean || true
    rm -rf ~/.gradle/caches/build-cache-* || true
    find . -name "*.kt" -exec touch {} \;  # 强制重新编译所有源文件

- name: Build Release APKs                 # --no-build-cache 禁用 Gradle 缓存
  run: ./gradlew assembleRelease --no-build-cache --rerun-tasks
```

### 坑记录

| 坑 | 原因 | 解法 |
|---|---|---|
| 推送后构建仍报旧错误 | Gradle build-cache 缓存了旧的 .class 文件 | workflow 中 `./gradlew clean` + `--no-build-cache` |
| ARM64 主机无法运行 aapt2 | aapt2 是 x86_64 二进制，QEMU 模拟性能极差 | 不在本地编译，强制用 GitHub Actions x86_64 原生构建 |
| 子代理修复的源码与远程不一致 | 子代理修改本地后未及时 push | 每轮修复后立即 push，验证远程再继续 |
| Docker buildx QEMU 编译 40 分钟未完成 | ARM → x86 交叉编译，每步都需 QEMU 模拟 | 放弃 Docker 方案，统一走 GitHub Actions |
| workflow_dispatch 未出现在 Actions 页面 | trigger 配置在 `on` 下但未在 workflow_dispatch 下正确声明 | 当前配置已修复，`workflow_dispatch` 独立声明 |

## 许可

内部项目