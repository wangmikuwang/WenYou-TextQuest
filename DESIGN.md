# 视觉设计约定

界面遵循 Material 3 规范，主题入口与实现位于 `ui/theme/`。以下为长期固化的设计约定，修改 UI 时保持一致。

## 动态取色

Android 12+ 且用户在设置中开启动态取色时，通过 `dynamicLightColorScheme` / `dynamicDarkColorScheme` 从系统壁纸生成调色板；其余情况回退 `Color.kt` 中定义的品牌色板。决策逻辑集中在 `ui/theme/Theme.kt` 的 `WenYouTheme`。

## 配色与组件用法

- 封面与角色头像统一使用 `AvatarPalette` 十二色调色板，形象以 Emoji 呈现，不依赖网络图片资源。
- 主操作使用 `Button`（primary）；列表卡片以 `surfaceContainerLow/High` 分层；提示信息使用 `tertiaryContainer`；错误提示使用 `errorContainer`。
- 角色对白气泡使用角色形象色的低透明度容器。避免大面积高饱和主色，强调色仅用于标题、图标、按钮文字等前景元素。
- 长文本正文允许选中复制。

## 形态与排版

- 组件圆角沿用 M3 形状体系：卡片 16–24dp，选项按钮 18dp 胶囊，封面与头像为圆形。
- 排版以 M3 字阶为基础（`ui/theme/Type.kt`），仅对正文的行高与字重做了针对长文阅读的调整。

## 明暗与导航

- 外观支持跟随系统 / 浅色 / 深色三态，在设置页即时切换，根主题实时响应。
- 五个 Hub 页共用底部 `NavigationBar`；窄屏优先单列阅读布局，Edge-to-Edge 由 `enableEdgeToEdge()` 处理状态栏。
