# 文游 TextQuest（WenYou TextQuest）

运行于 Android 的文字冒险游戏平台：支持完全离线的分支剧情，也支持接入第三方大模型 API 获得 AI 场景生成与 AI 导演自由模式。技术栈为 Kotlin、Jetpack Compose、Material 3；所有数据以 JSON 保存在应用私有目录。

## 版本（Product Flavors）

工程定义两个可独立安装的 flavor：

- `alpha`（文游 α）：内置 LGBT 与成人向预设，设置页提供内容开关；
- `beta`（文游 β）：仅内置非 LGBT 预设，不提供内容开关入口。

构建配置见 `app/build.gradle.kts`。内容开关只影响列表过滤，不删除本地数据；过滤逻辑位于 `ui/vm/LibraryViewModel.kt`。

## 功能

- 节点式分支引擎：节点分为叙述（`NARRATION`）、AI 生成场景（`AI`）、结局（`ENDING`）三类。支持节点进入效果、选项显示条件与选择效果、数值变量、场景标记、掷骰，以及 `${变量}` 文本插值，实现在 `data/engine/GameEngine.kt`。
- 角色卡：以名字、Emoji、性格、说话风格、背景、台词示范等字段构成角色人设，编辑后注入 AI 系统提示；对局中可维护角色的 0..100 状态值与标记，定义见 `data/model/CharacterMetrics.kt`。
- 多品牌 AI 接入：OpenAI 兼容协议覆盖 DeepSeek、Kimi、GLM、Qwen、豆包、OpenRouter、硅基流动、小米 MiMo、Ollama 等服务；Anthropic 与 Gemini 分别走 Messages API 与 `streamGenerateContent` 原生协议。统一为 SSE 流式输出，提供连接测试与模型列表拉取，客户端实现见 `data/llm/ChatClient.kt`，品牌预设见 `data/llm/Catalog.kt`。
- 对局存档：支持随时存档、主页续玩，以及整包 JSON 导出 / 导入。

## 构建

编译环境要求：`compileSdk 34`、`minSdk 26`、JDK 17。仓库自带 Gradle wrapper（8.9），可直接用 Android Studio（Ladybug 或更新）打开运行。

命令行构建示例：

```bash
./gradlew :app:assembleAlphaDebug
./gradlew :app:assembleBetaDebug
```

版本号维护在 `version.properties`；执行 `./gradlew bumpVersion` 会递增 `patch` 与 `versionCode`（任务定义于 `app/build.gradle.kts`）。正式打包前应先执行该任务。

## 目录结构

```text
app/src/main/java/io/wenyou/textquest/
├── data/model/    持久化模型，JSON 序列化字段对手工编辑友好
├── data/engine/   分支引擎：条件、效果、掷骰、模板插值，纯逻辑无 IO
├── data/ai/       AI 场景与导演的提示词组装、模型 JSON 输出解析
├── data/llm/      多协议流式客户端与品牌预设目录
├── data/repo/     本地 JSON 资料库与 SharedPreferences 设置
├── data/sample/   首次启动植入的示例角色与剧情
└── ui/            Compose 页面、ViewModel、主题
```

## 设计决策与已知限制

- 未引入 Hilt 与 Room：依赖注入在 `WenYouApp` 中手动完成，持久化直接读写 JSON 文件。该方案减少了框架与迁移成本，但所有写入需要由调用方保证串行；备份即复制文件。
- AI 上下文取最近 `historyWindow` 条日志，超出部分自动截断，以避免提示词超长。
- 流式生成结束前不写入对局日志，因此生成过程中无法保存“半句”内容；整段结束后存档即为一致状态。
- 内置预设包按资源文件名记录合并状态（`SettingsStore` 的 `preset_files_applied_v2`），合并规则为按 id 只补不覆盖；用户已删除的内置内容不会在后续启动时被自动写回。
