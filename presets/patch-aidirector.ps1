$ErrorActionPreference='Stop'
$path='F:\OneDrive\Documents\harness\WenYouTextQuest\app\src\main\java\io\wenyou\textquest\data\ai\AiDirector.kt'
$t=[System.IO.File]::ReadAllText($path)

# AiScene 加 reasoning：在 choices 行与 ) 之间插入
$t=[regex]::Replace($t,'(val choices: List<AiChoice> = emptyList\(\),\r?\n)(\))','$1    val reasoning: String = ""`r`n$2')

# 两个方法的签名：onDelta 后加 onReasoning
$t=[regex]::Replace($t,'(        onDelta: \(String\) -> Unit = \{\}\r?\n)(    \): AiScene \{)','$1        onReasoning: (String) -> Unit = {}`r`n$2')

# generateScene 调用
$t=[regex]::Replace($t,'(        val user = contextTail\(story, state\) \+ stateSnapshot\(state\) \+ charStatesSnapshot\(story, characters, state\) \+ scaleNote\(adult\)\r?\n)(        val raw = client\.streamText\(profile, system, user, ChatOptions\(story\.ai\.temperature, story\.ai\.maxTokens\), onDelta\)\r?\n)(        return parseScene\(raw\))','$1        val result = client.streamText(profile, system, user, ChatOptions(story.ai.temperature, story.ai.maxTokens), onDelta, onReasoning)`r`n        return parseScene(result.content).copy(reasoning = result.reasoning)')

# directorTurn 调用（playerText 变体）
$t=[regex]::Replace($t,'(        val user = contextTail\(story, state, playerText\) \+ stateSnapshot\(state\) \+ charStatesSnapshot\(story, characters, state\) \+ scaleNote\(adult\)\r?\n)(        val raw = client\.streamText\(profile, system, user, ChatOptions\(story\.ai\.temperature, story\.ai\.maxTokens\), onDelta\)\r?\n)(        return parseScene\(raw\))','$1        val result = client.streamText(profile, system, user, ChatOptions(story.ai.temperature, story.ai.maxTokens), onDelta, onReasoning)`r`n        return parseScene(result.content).copy(reasoning = result.reasoning)')

# testProfile
$t=[regex]::Replace($t,'(        return client\.streamText\(\r?\n            profile, system, user,\r?\n            ChatOptions\(temperature = 0\.2, maxTokens = 16\)\r?\n        \)\.trim\(\))','        return client.streamText(`r`n            profile, system, user,`r`n            ChatOptions(temperature = 0.2, maxTokens = 16)`r`n        ).content.trim()')

[System.IO.File]::WriteAllText($path,$t,(New-Object System.Text.UTF8Encoding($false)))
Write-Output ("AiScene reasoning: "+$t.Contains('val reasoning: String = ""'))
Write-Output ("onReasoning count: "+([regex]::Matches($t,'onReasoning: \(String\) -> Unit = \{\}')).Count)
Write-Output ("copy reasoning count: "+([regex]::Matches($t,'\.copy\(reasoning = result\.reasoning\)')).Count)
Write-Output ("testProfile content: "+$t.Contains(').content.trim()'))
