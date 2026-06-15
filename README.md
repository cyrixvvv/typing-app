# 星星打字通 (HongluTyping)

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
        │   │   ├── AdvancedModeActivity.kt    # 高级模式: WPM 速度测试
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

```bash
cd typing-app
# 需要 Android SDK + JDK 8/11
./gradlew assembleDebug   # Debug 包
./gradlew assembleRelease # Release 包 (启用 ProGuard)
```

### 依赖清单

| 依赖 | 版本 | 用途 |
|---|---|---|
| kotlin | 1.8.22 | 编程语言 |
| androidx.leanback | 1.1.0 | TV 界面 |
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

## 许可

内部项目
