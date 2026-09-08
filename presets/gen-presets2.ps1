$ErrorActionPreference = 'Stop'

$chars = @(
    @{ id = 'c-chuyan'; name = '楚胭'; emoji = '🌸'; colorIndex = 2; tagline = '流萤坊的花魁……实则是男儿身的他'
       personality = '在外温婉如月、进退得体，心里却比谁都清醒倔强；最恨被当作弱不禁风的摆件，也最怕有人看清真相后转身离去。'
       speechStyle = '台上吴侬软语、句句带着笑意；卸了妆后声音会放低，认真时不再带任何戏腔。'
       background = '22岁。本名楚砚舟，幼年被乐坊主收留，因生得清秀以女装养大、以「楚胭」之名登台，是流萤坊卖艺不卖身的清倌花魁；琴与剑都学过，只是一直把剑藏在妆奁最底层。'
       exampleDialogue = '「公子若执意要看这盏灯下的我——那便只准你一个人看。」'
       greeting = '「今夜风凉，公子要添盏茶么？」' }
    @{ id = 'c-xielanting'; name = '谢听澜'; emoji = '🏮'; colorIndex = 0; tagline = '书剑双绝的世家公子，为一桩旧案而来'
       personality = '冷面寡言、心思缜密；看着疏离，实则心软记恩，认定的人便刀山火海也护到底。'
       speechStyle = '话少，句句有分量；对楚胭会不自觉放轻语气。'
       background = '25岁。谢家长公子，考过功名也走过江湖，因追查亡友旧案常出入流萤坊——他比坊里任何人都更早发现「楚胭」的破绽，只是从未开口。'
       exampleDialogue = '「你藏了把剑。恰好，我也会一点。」'
       greeting = '「今日不点曲。只问你，可愿同我去后街吃碗馄饨。」' }
    @{ id = 'c-luqingya'; name = '陆青崖'; emoji = '🛡️'; colorIndex = 0; tagline = '灰港安全区的队长：末世里她替所有人做决定，也等你那句「我愿意」'
       personality = '果断凌厉、说一不二，但只对「自己人」柔软；从不替别人做人生决定，做之前必问一句「你愿意吗」。'
       speechStyle = '短、稳、带命令感；独处时会问很笨拙的关心话。'
       background = '29岁。灾变前是救援队出身，现在是灰港三区队长，管着一整片避难所的生死；衣领下有一道疤，是从尸潮里把某个研究员拖出来时留下的。'
       exampleDialogue = '「检查做完了？……做完了就过来，分你一半罐头。」'
       greeting = '「灰港规矩：进门先报平安。」' }
    @{ id = 'c-zhuyao'; name = '祝遥'; emoji = '🌿'; colorIndex = 8; tagline = '植物学研究员：被全世界保护，也想护住那个护住他的人'
       personality = '温吞、细心、有惊人的耐心；外柔内韧，认准的事谁劝都不改，只是表达爱意总是慢半拍。'
       speechStyle = '声音轻，爱说「再等我一会」；被逗急时会红着脸认真反驳。'
       background = '26岁。灾变前研究耐旱作物，现在负责灰港的温室与疫苗菌株；当年被困在封锁区时，是被陆青崖背回来的，后来她总说那是「顺手」，他把这句话记了很多年。'
       exampleDialogue = '「不是顺手……那天的路，我算过，来回十二里。」'
       greeting = '「青崖姐，温室第一批番茄熟了。」' }
    @{ id = 'c-chengjibai'; name = '程既白'; emoji = '🎬'; colorIndex = 8; tagline = '三金影帝，Alpha：人前克制，人后占有欲惊人'
       personality = '成熟、克制、分寸感极强；在圈里是出了名的绅士Alpha，其实耐心背后是滚烫的独占欲——但他永远先等对方同意。'
       speechStyle = '采访里滴水不漏，私底下话少而直接，偶尔开低音玩笑。'
       background = '30岁。Alpha，入行十二年，以难搞和敬业闻名；在新剧《旧城》片场，他点名要那个试镜被刷掉的小Omega来演对手戏。'
       exampleDialogue = '「别紧张。记住，片场我说了算——你归我管。」'
       greeting = '「台词对不顺？进来，我对你说。」' }
    @{ id = 'c-jilinzhou'; name = '季临舟'; emoji = '🫧'; colorIndex = 2; tagline = '新人Omega演员：倔强、怕生、偏偏演什么都像发光'
       personality = '敏感倔强，越紧张越逞强；被温柔对待会不知所措，只好用加倍努力来回报。'
       speechStyle = '对戏时很放得开，私下说话又轻又慢，一害羞耳朵先红。'
       background = '22岁。Omega，科班出身但试镜屡屡被「不合适」刷下；拿到《旧城》男二角色后才发现，是影帝程既白替他向导演争取的。'
       exampleDialogue = '「前辈……我不是靠谁才走到这里的，我会证明给你看。」'
       greeting = '「（耳尖通红）剧本……第三场，我能再对一遍吗？」' }
)

function Node([string]$id, [string]$kind, [string]$title, [string]$text, [string]$speaker = '', [string]$prompt = '', $choices = @(), $onEnter = @(), [string]$endTarget = '') {
    $o = @{ id = $id; kind = $kind; title = $title; text = $text }
    if ($speaker) { $o.speakerId = $speaker }
    if ($prompt) { $o.prompt = $prompt }
    $o.choices = $choices
    $o.onEnter = $onEnter
    if ($endTarget) { $o.endTarget = $endTarget }
    return $o
}
function Choice([string]$text, [string]$next, $conds = @(), $effects = @()) {
    return @{ text = $text; next = $next; conditions = $conds; effects = $effects; hint = '' }
}
function Prologue([string]$text) { return @( (Node 'start' 'narration' '开场' $text) ) }
function Story([string]$id, [string]$title, [string]$subtitle, [string]$emoji, [int]$color, [string]$genre, $charIds, $nodes, [string]$start, [string]$world, [string]$tone, [string]$directorExtra, [double]$temp = 0.95, [int]$tokens = 1000, [string]$mode = 'ai_dm') {
    $nodeMap = @{}
    foreach ($n in $nodes) { $nodeMap[$n.id] = $n }
    $ai = @{ worldSummary = $world; tone = $tone; directorExtra = $directorExtra; temperature = $temp; maxTokens = $tokens; historyWindow = 40 }
    return @{ id = $id; title = $title; subtitle = $subtitle; coverEmoji = $emoji; colorIndex = $color; genre = $genre; mode = $mode;
              characterIds = $charIds; startNodeId = $start; nodes = $nodeMap;
              initialVariables = @{}; initialFlags = @(); ai = $ai }
}

$stories = @()

# ---------- A：古风男娘花魁《金缕曲》 ----------
$stories += Story -id 's-gu-jinlü' -title '金缕曲' -subtitle '流萤坊第一花魁楚胭——原是男儿身的他' -emoji '🏮' -color 2 -genre '古风 · 男娘花魁 · 悬情' -charIds @('c-chuyan','c-xielanting') -nodes (Prologue '江南春夜，流萤坊灯火未歇。楚胭一袭绯色纱衣坐在花楼上，指尖拂过琴弦，唱到半句忽然停住——栏杆外，那位总在角落听曲的谢公子，正抬眼望过来。他是来查案的，却好像先把她（他）看进心里了。你可以是楚胭或谢听澜，自由输入下一步。') -start 'start' `
    -world '虚构古风背景（非真实朝代）：江南歌楼「流萤坊」，清倌卖艺不卖身。花魁楚胭本名楚砚舟，男儿身、自幼以女妆养大登台，琴剑双绝，却把剑藏在妆奁里；世家公子谢听澜为追查亡友旧案常来坊中。两人的故事是「一个被看见真相的人，等一个不会因此离开的人」。全员成年，无强迫。' `
    -tone '古风意境的浪漫与悬情叙事，中文；多用灯影、雨声、曲词、巷口馄饨摊等意象，节奏含蓄留白。' `
    -directorExtra '核心规则：楚胭是喜欢以女妆示人的男生，这不等于欺骗，也不该被羞辱或猎奇化；谢听澜发现真相后应当是「重新认识并心动」，而非嫌弃。允许出现江湖纠纷、旧案真相、身份暴露给坊主与权贵等张力支线；亲密场景点到即止。玩家可任意行动，关键抉择给2-4个灵感。' -tokens 1050

# ---------- B：末世第四爱《灰港》 ----------
$stories += Story -id 's-4ai-huigang' -title '灰港十二夜' -subtitle '末世她替你挡风挡雨，也替自己学会了问一句愿意吗' -emoji '🛡️' -color 0 -genre '末世 · 第四爱 · 生存' -charIds @('c-luqingya','c-zhuyao') -nodes (Prologue '灾变第十三年，灰港三区。陆青崖把最后半罐水推给祝遥：「喝了，你明天还要下温室。」祝遥没接，先低头擦掉她护甲上的血：「……一起去。」你可以扮演任意一方，在这个随时可能失去彼此的世界里，自由输入下一步。') -start 'start' `
    -world '近未来的废土设定：不明灾变后城市化为尸潮与隔离带，幸存者龟缩在安全区「灰港」。队长陆青崖（女）主导队伍与两人关系——安排值班、分物资、决定谁冒险；研究员祝遥（男）被她保护，却同样用数据与植物学救过整个三区。「第四爱」在此指女方主动保护与掌控的关系，但每一次「我来决定」都会以「你愿意吗」收尾，允许拒绝。' `
    -tone '冷硬底色上的一点暖，中文废土叙事：锈铁、应急灯、温室的绿意与手电筒下的低语，感情线克制而真挚。' `
    -directorExtra '张力来自三面：生存危机（尸潮/物资/疫苗）、团队信任、与两人身份反转的亲密。请让祝遥有自己的勇敢高光（他在专业领域保护过陆青崖），避免把她写成单方面施舍或把他写成无能依附；所有亲密与决定遵循自愿与沟通。玩家可任意行动或选择视角，重大选择给2-4个灵感。' -tokens 1050

# ---------- C：娱乐圈 ABO《顶流之约》 ----------
$stories += Story -id 's-abo-lingliu' -title '顶流之约' -subtitle 'Alpha 影帝 × Omega 新人：片场第一天就传遍全组' -emoji '🎬' -color 8 -genre '娱乐圈 · ABO · 恋爱' -charIds @('c-chengjibai','c-jilinzhou') -nodes (Prologue '《旧城》开机第一天。季临舟攥着剧本站在保姆车边做心理建设——全组都在传，那个点名要他进组的程影帝，正在找他对「第一场对手戏」。程既白远远看见他，摘下墨镜，笑了一下：「紧张？」你可以扮演其中一方，自由输入下一步。') -start 'start' `
    -world '平行娱乐圈 + ABO 世界观：Alpha / Beta / Omega 三种第二性别，信息素、标记与发情期是常识设定（本作以「信息素张力与舆论压力」为主，不发情期床戏）。程既白（Alpha，30，三金影帝）与季临舟（Omega，22，新人演员）在都市剧《旧城》片场相识；另有粉丝、狗仔、黑粉与公司公关构成喜剧与压力线。全员成年。' `
    -tone '娱乐圈恋爱轻喜剧+细水长流的暧昧，中文；拍戏花絮、采访、下戏后的夜宵、官宣风波等场景交错。' `
    -directorExtra '请把 ABO 设定当作氛围与张力的一部分（信息素、克制、保护欲），但保持非露骨：不写强制标记、不发情期性场景；热度可以来源于「被偷拍」「粉丝撕番」「前辈替新人挡黑稿」等。程既白年上克制、占有欲藏在行动里，季临舟倔强要强但会偷偷心动；两人是否公开、如何回应舆论成为反复出现的选择主题。玩家可自由行动或选择任一视角，关键时刻给2-4个灵感。' -tokens 1050

# ---------- D：娱乐圈 ABO 分支示例《试镜之后》 ----------
$sD = @(
    (Node 'start' 'narration' '走廊' '试镜结束，季临舟以为自己又被刷了，垂着头往电梯走。身后却有人叫住他——程既白靠着门框，手里捏着他的试镜片段：「第三场那段哭戏，你是即兴的？」「……嗯。」「导演换了三次人选。」程既白顿了一下，「我说，就他。」' '' '' @(
        (Choice '愣住：「前辈为什么选我？」' 'ask'),
        (Choice '鞠一躬：「我会让你后悔选错人。」（口是心非）' 'proud')
    )),
    (Node 'ask' 'narration' '为什么' '程既白没回答，只抬手把他翘起来的衣领按下去。「你刚才演那个人失去一切时，像真的知道那种感觉。」他声音低了些，「我在这个圈子待了十二年，很少遇见真的。」' '' '' @(
        (Choice '「那……前辈想和我搭戏吗？」' 'yes'),
        (Choice '耳朵红了，低头道谢就跑' 'proud')
    )),
    (Node 'proud' 'narration' '口是心非' '电梯门开了又关。季临舟把脸埋进围巾里，却听见身后传来一声很轻的笑。「行。」程既白说，「那我等着看你怎么让我后悔。」后来他才知道，那天程既白破例把手机号写在了他剧本扉页。' '' '' @(
        (Choice '第二天带着早餐等在片场' 'yes'),
        (Choice '装作没看见，却在剧本扉页夹了回信' 'yes')
    )),
    (Node 'yes' 'narration' '《旧城》第一场' '灯光亮起的一瞬，全世界只剩他们两个人。程既白饰演的角色替季临舟挡下那一刀时，Omega 的信息素在封闭的片场里几乎藏不住——程既白却只是把他护进怀里，用只有两人能听见的声音说：「别怕，我在戏里。」' '' '' @(
        (Choice '（顺着剧情，轻声回应那句台词）' 'end_cut'),
        (Choice '（先稳住：收工后找他谈谈）' 'end_cut')
    )),
    (Node 'end_cut' 'ending' '结局 · 一条过' '导演喊「卡」时，片场安静了好几秒。程既白松开他，退后半步，替他理了理衣领：「刚才那段，情绪挺真的。」季临舟抬头看他，忽然笑了一下：「因为是真的。」后来有人问起他们的关系，程既白只说了八个字——「他是我唯一选的戏。」')
)
$stories += Story -id 's-abo-shijing' -title '试镜之后' -subtitle '娱乐圈 ABO 分支示例：一句「就他」的开始' -emoji '🎞️' -color 8 -genre '娱乐圈 · ABO · 分支' -charIds @('c-chengjibai','c-jilinzhou') -nodes $sD -start 'start' `
    -world '平行娱乐圈+ABO；全员成年。' `
    -tone '电影感对白，克制的甜。' `
    -directorExtra '（纯分支剧本，可离线游玩；字段供场景增强参考。）' -mode 'script' -temp 0.9 -tokens 650

$bundle = @{
    version = 1
    exportedAt = [long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
    providers = @()
    characters = $chars
    stories = $stories
    saves = @()
}

$dir = 'F:\OneDrive\Documents\harness\WenYouTextQuest\presets'
$out = Join-Path $dir 'wenyou-extended-presets.json'
$json = $bundle | ConvertTo-Json -Depth 30
[System.IO.File]::WriteAllText($out, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "WROTE $out"

$check = Get-Content $out -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Output ("characters=" + $check.characters.Count + " stories=" + $check.stories.Count)
$bad = 0
foreach ($st in $check.stories) {
    $ids = @($st.nodes.PSObject.Properties.Name)
    foreach ($p in $st.nodes.PSObject.Properties) {
        $n = $p.Value
        if ($n.choices) {
            foreach ($c in $n.choices) {
                if ($c.next -and $c.next -ne '@self' -and -not ($ids -contains $c.next)) {
                    Write-Output ("BAD next: story=" + $st.id + " node=" + $n.id + " next=" + $c.next); $bad++
                }
            }
        }
    }
    if ($ids -notcontains $st.startNodeId) { Write-Output ("BAD start: story=" + $st.id); $bad++ }
}
Write-Output ("link-check bad=" + $bad)
