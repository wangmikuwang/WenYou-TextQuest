# Material You 设计遵循说明

本应用的设计依据 Android AOSP 文档
[*Material You 设计*](https://source.android.com/docs/core/display/material?hl=zh-cn)
（壁纸取色 / 色调角色 / 容器色 / 圆角形态 / 动效与自适应）实现如下：

## 1. 动态取色（Dynamic Color）

- `ui/theme/Theme.kt`：Android 12+ 且设置开启「动态取色」时，通过
  `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` 从壁纸生成整套
  Material You 调色板；`primary/secondary/tertiary` 与各类 `*Container` 色调角色全部由系统接管。
- Android 12 以下或关闭开关时回退品牌主题（`Color.kt` 中性紫基线），并同样提供明/暗两套。

## 2. 色调角色与强调色用法

- 主操作：`Button`（primary）用于「新建 / 开始 / 保存」等前景强调；
- 容器：卡片用 `surfaceContainerLow/High` 分层，信息/提示用 `tertiaryContainer`，
  角色对白气泡用角色形象色的低透明度容器，错误用 `errorContainer` —— 避免大面积高饱和主色，
  强调色只用于前景元素（标题、图标、按钮文字、链接式操作）。
- 导航与选择：M3 `NavigationBar`（含 5 个 hub 页）、`FilterChip`、`AssistChip`、
  `ExposedDropdown`、`Switch`、`Slider`、`Snackbar`、`AlertDialog`、Extended FAB。

## 3. 形态（Shape）与排版

- 全局沿用 M3 形状体系（`RoundedCornerShape` 家族），封面/头像为圆（`CircleShape`），
  卡片 16–24dp 圆角，选项按钮 18dp 胶囊，符合「柔和大圆角、少直角」的 Material 表达。
- `Type.kt` 以 M3 字阶为基，为长文阅读微调行高/字重；正文支持选择复制。

## 4. 明暗与动效

- 三种外观模式：跟随系统 / 浅色 / 深色（设置页即时切换，根主题实时响应）。
- 流式 AI 打字机光标、加载态 `CircularProgressIndicator`、对局自动滚动、
  顶部 AppBar 等动效反馈；按钮/卡片有 M3 默认涟漪与高度变化。

## 5. 自适应排版

- Hub 页统一 Scaffold + 底部 NavigationBar；窄屏（手机）单列阅读优先。
- 深色下使用对色阶调色板，保证可读性；Edge-to-Edge 由 `enableEdgeToEdge()` 接管状态栏。

## 6. 视觉一致性约定

- 角色/剧情封面使用同一套 12 色调色板（`AvatarPalette`），图标 Emoji 承载形象，
  避免依赖网络图片；圆角胶囊标签（Pill）表达元信息（模式/节点数/AI 标记等）。
