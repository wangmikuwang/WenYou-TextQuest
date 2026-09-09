$ErrorActionPreference = 'Stop'
$path='F:\OneDrive\Documents\harness\WenYouTextQuest\app\src\main\java\io\wenyou\textquest\data\ai\AiDirector.kt'
$t=[System.IO.File]::ReadAllText($path)
# 1) 两个方法的签名加 adult 参数
$t=[regex]::Replace($t,'        onDelta: \(String\) -> Unit = \{\}\r?\n    \): AiScene \{','        adult: Boolean = false,`r`n        onDelta: (String) -> Unit = {}`r`n    ): AiScene {')
# 2) 两个 user 行末尾追加尺度说明
$t=$t.Replace('val user = contextTail(story, state) + stateSnapshot(state) + charStatesSnapshot(story, characters, state)',
 'val user = contextTail(story, state) + stateSnapshot(state) + charStatesSnapshot(story, characters, state) + scaleNote(adult)')
$t=$t.Replace('val user = contextTail(story, state, playerText) + stateSnapshot(state) + charStatesSnapshot(story, characters, state)',
 'val user = contextTail(story, state, playerText) + stateSnapshot(state) + charStatesSnapshot(story, characters, state) + scaleNote(adult)')
# 3) charStates 固定话术简化
$t=$t.Replace('append("（请让角色言行贴合以上状态；氛围数值只影响语气与暧昧尺度，不写露骨内容。）\n")','append("（请让角色言行贴合以上状态。）\n")')
# 4) 在 charStatesSnapshot 注释前插入 scaleNote
$anchor='    /** 角色当前状态（好恶/身体/穿着/氛围值等）注入上下文。 */'
$helper=@'
    /** 尺度说明：成人向允许亲密/性爱（成年、自愿），否则浪漫含蓄。 */
    private fun scaleNote(adult: Boolean): String =
        if (adult) "\n【本作为18+成年向】允许自然描写亲密与性爱场景，保持成年、自愿、可随时停下；不写强制/未成年。\n"
        else "\n【内容尺度】保持浪漫含蓄、非露骨，亲密点到即止。\n"

'@
$t=$t.Replace($anchor,$helper+$anchor)
[System.IO.File]::WriteAllText($path,$t,(New-Object System.Text.UTF8Encoding($false)))
Write-Output ("adult param count: "+([regex]::Matches($t,'adult: Boolean = false')).Count)
Write-Output ("scaleNote present: "+$t.Contains('scaleNote'))
Write-Output ("notes removed: "+(-not $t.Contains('不写露骨内容')))