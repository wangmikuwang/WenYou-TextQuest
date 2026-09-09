# 文游 TextQuest

自己编剧情、自己造角色、想接哪家 AI 就接哪家的 Android 文字游戏。
Kotlin + Jetpack Compose 写的，纯分支剧本离线能玩，接上 AI 之后才有“AI 场景”和“AI 导演自由模式”。

仓库在 `F:\OneDrive\Documents\harness\WenYouTextQuest`。

## 两个版本

一个工程打两种可同装的包（flavor 见 `app/build.gradle.kts`）：

- `alpha`（文游 α）：内置 LGBT 与成人向预设，设置里有内容开关
- `beta`（文游 β）：只内置非 LGBT 预设，不带内容开关

内容开关只是列表过滤，删不删数据是另一回事，过滤逻辑在 `ui/vm/LibraryViewModel.kt`。

## 能玩什么

- 节点式分支剧本：叙述 / AI 生成场景 / 结局三种节点。节点带进入效果，
  选项带显示条件与效果，支持变量、标记、掷骰，正文里写 `${变量}` 做插值。
- 角色卡：名字、Emoji、性格/说话方式/背景/台词示范，编辑后作为 AI 人设注入。
  剧情里还可以给角色加 0..100 的状态值（好感、信任、精力这类），对局时从抽屉看。
- 多品牌 AI：OpenAI 兼容协议一家覆盖 DeepSeek/Kimi/GLM/Qwen/豆包/OpenRouter/Ollama，
  Claude 和 Gemini 走各自的原生协议。统一流式、打字机输出；页面里能测连接、拉模型列表。
- 存档：随时可存，主页继续上次，整包 JSON 导出导入。

## 构建

用 Android Studio（Ladybug 或更新）打开本目录就能跑，仓库带 wrapper。
要求 compileSdk 34、minSdk 26、JDK 17。

命令行打 debug 包：

```bash
./gradlew :app:assembleAlphaDebug   # 或 assembleBetaDebug
```

版本号由 `./gradlew bumpVersion` 维护：patch +1 并同步 versionCode，
脚本写在 `app/build.gradle.kts` 里。

## 目录速览

```text
app/src/main/java/io/wenyou/textquest/
├── data/model/    序列化模型，JSON 手改友好
├── data/engine/   分支引擎（条件/效果/掷骰/插值），纯逻辑
├── data/ai/       AI 场景与导演的提示词组装、JSON 输出解析
├── data/llm/      三家协议的流式客户端 + 品牌预设
├── data/repo/     本地 JSON 库与设置
├── data/sample/   首次启动植入的示例
└── ui/            Compose 页面、VM、主题
```

## 已知取舍

- 没上 Hilt/Room：注入手写，数据直接存 JSON 文件。少一层框架，
  代价是写入要自己保证串行，备份就是拷文件。
- AI 上下文取最近 `historyWindow` 条，长了自动截。
- 流式生成途中不保存“半句”，整段结束才写日志，此时存档才一致。
- 内置预设包按 id 合并一次、只补不覆盖。已经合并过的文件记录在
  `SettingsStore`（`preset_files_applied_v2`），用户删掉的内置内容不会再被自动补回来。
