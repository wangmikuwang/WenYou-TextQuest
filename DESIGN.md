# 视觉与设计约定

UI 直接跟 Material 3 走。Android 12+ 且开了动态取色就用
`dynamicLightColorScheme` / `dynamicDarkColorScheme`（见 `ui/theme/Theme.kt`），
低版本或关闭开关回退 `Color.kt` 里的品牌色板。

改 UI 时照着这几条来：

- 封面、角色头像共用 `AvatarPalette` 的 12 色，图标用 Emoji，不依赖网络图。
- 卡片用 `surfaceContainerLow/High` 分层；提示信息用 `tertiaryContainer`，
  错误用 `errorContainer`，避免大面积高饱和主色。
- 长文本正文允许选中复制；选项按钮统一胶囊圆角。
- 明暗三态（跟随系统 / 浅色 / 深色）在设置页即时切换。
- 排版沿用 M3 字阶，`Type.kt` 只调过正文的行高字重。
