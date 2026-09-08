$ErrorActionPreference = 'Stop'
$chars = @(
    @{ id='c-shenyu'; name='沈聿'; emoji='📚'; colorIndex=5; tagline='校草：成绩好，脾气也最好'
       personality='温和、可靠、慢半拍；认真起来一塌糊涂。'
       speechStyle='说话轻声，偶尔讲冷笑话。'
       background='20岁，大二校草，图书馆常客；耳根软，被同桌的江晴“骗”去义务补习。'
       exampleDialogue='「你先把这道题做完，我再给你讲下一个。」'
       greeting='「又来啦？坐我旁边。」' }
    @{ id='c-jiangqing'; name='江晴'; emoji='✏️'; colorIndex=4; tagline='学霸同桌：嘴上争强，心里很软'
       personality='好胜、嘴硬、死要面子；其实很细心，谁的笔记都肯借。'
       speechStyle='语速快，爱抬杠，急了会脸红。'
       background='19岁，大一，同桌兼学霸，总为一道题跟沈聿争到放学；争着争着就成了泡图书馆的搭档。'
       exampleDialogue='「我才不是来问你的！……这题怎么写？」'
       greeting='「喂，今天图书馆占我旁边的座。」' }
    @{ id='c-suxiao'; name='苏晚'; emoji='🍲'; colorIndex=2; tagline='姐姐：把日子熬成汤，也熬软了心'
       personality='温柔能干、隐忍务实；习惯了照顾人，却忘了照顾自己。'
       speechStyle='平和唠叨，关心都藏在“多吃点”里。'
       background='28岁，中学教师，独自撑起老屋与菜园；守着一个常年在外、突然说要回家的弟弟。'
       exampleDialogue='「回来就回来，给你留着灯呢。」'
       greeting='「进屋洗手，汤在锅里。」' }
    @{ id='c-sucheng'; name='苏城'; emoji='🧳'; colorIndex=8; tagline='弟弟：常年在外，终于学会把家记在心里'
       personality='倔、闷、不会说软话；漂泊久了，原来最想的是那碗热汤。'
       speechStyle='话少，笨拙，被戳中心事会别过头。'
       background='24岁，在外打拼多年，这次带着一袋东西和一句没说出口的话回老家。'
       exampleDialogue='「姐，我……回来了。」'
       greeting='「路上在车里睡着了，闻见汤味才醒。」' }
    @{ id='c-linyue'; name='林越'; emoji='💼'; colorIndex=0; tagline='上司：雷厉风行，唯独对某人例外'
       personality='果断、专业、气场强；私下里其实会偷偷记住下属的咖啡口味。'
       speechStyle='干练带命令感，说软话时反而显得生硬。'
       background='33岁，创意总监；对副总监周谨要求严厉，却总在 deadline 深夜把加班餐放到他桌上。'
       exampleDialogue='「方案改完再走。……嗯，走了也行，明天再改。」'
       greeting='「这么早？杯咖啡你也带一杯。」' }
    @{ id='c-zhoujin'; name='周谨'; emoji='📋'; colorIndex=1; tagline='副总监：能力很强，唯独在她面前会慌'
       personality='严谨、拼命、自尊心强；越在意越像在较劲。'
       speechStyle='正经，偶尔被看穿就词穷。'
       background='27岁，创意部副总监，和上司林越从“对手”到“搭档”；办公桌抽屉里藏着一张没敢送的票。'
       exampleDialogue='「这一版我重做了——你不用再看，先休息。」'
       greeting='「要点review的话，我正好有时间。」' }
)
function Node([string]$id,[string]$kind,[string]$title,[string]$text,[string]$speaker='',[string]$prompt='',$choices=@(),$onEnter=@(),[string]$endTarget=''){ $o=@{id=$id;kind=$kind;title=$title;text=$text}; if($speaker){$o.speakerId=$speaker}; if($prompt){$o.prompt=$prompt}; $o.choices=$choices; $o.onEnter=$onEnter; if($endTarget){$o.endTarget=$endTarget}; return $o }
function Choice([string]$text,[string]$next,$conds=@(),$effects=@()){ return @{text=$text;next=$next;conditions=$conds;effects=$effects;hint=''} }
function Prologue([string]$text){ return @( (Node 'start' 'narration' '开场' $text) ) }
function Story([string]$id,[string]$title,[string]$subtitle,[string]$emoji,[int]$color,[string]$genre,$charIds,$nodes,[string]$start,[string]$world,[string]$tone,[string]$directorExtra,[double]$temp=0.95,[int]$tokens=1000,[string]$mode='ai_dm'){ $nm=@{}; foreach($n in $nodes){$nm[$n.id]=$n}; $ai=@{worldSummary=$world;tone=$tone;directorExtra=$directorExtra;temperature=$temp;maxTokens=$tokens;historyWindow=40}; return @{id=$id;title=$title;subtitle=$subtitle;coverEmoji=$emoji;colorIndex=$color;genre=$genre;mode=$mode;characterIds=$charIds;startNodeId=$start;nodes=$nm;initialVariables=@{};initialFlags=@();ai=$ai} }
$stories=@()
# 校园 AI导演
$stories += Story -id 's-bare2-chunri' -title '春日补习班' -subtitle '从一道错题开始的相熟' -emoji '🌱' -color 4 -genre '校园 · 青春 · 恋爱' -charIds @('c-shenyu','c-jiangqing') -nodes (Prologue '大二的图书馆里，校草沈聿又被同桌江晴“骗”来义务补习。本想装酷一分钟，结果被她一道错题问住了，两人从争到笑，把一整个春天耗在了同一张桌上。你可以扮演其中一方，自由输入下一步。') -start 'start' `
    -world '大学校园图书馆，春日。沈聿（男，20）与江晴（女，19）从“互相看不上”到成为泡馆搭档；主线是青涩的靠近。全员成年、纯校园向。' `
    -tone '轻松明亮的校园叙事，中文：教室、图书馆、占座与橡皮擦。' `
    -directorExtra '以「借笔记、占座、躲雨、社团活动、表白」等校园日常推进，感情线青涩含蓄；可自由选择谁先心动。保持双方的别扭与真诚，允许牵手/告白但不写露骨。玩家可自由行动或选择视角。' -tokens 1000
# 家庭 AI导演
$stories += Story -id 's-bare2-jiuwo' -title '旧屋与粥' -subtitle '漂泊再远，也要记得回家的路' -emoji '🏡' -color 2 -genre '家庭 · 温情 · 治愈' -charIds @('c-suxiao','c-sucheng') -nodes (Prologue '老屋的灯还亮着，粥在锅里咕嘟。姐姐苏晚守着菜园和一张旧木桌，弟弟苏城带着背包和一句没说出口的话，终于在深夜敲响了门。家常与和解，就从这碗粥开始。你可以扮演其中一方，自由输入下一步。') -start 'start' `
    -world '南方小城的老屋。苏晚（女，28，教师）与弟弟苏城（男，24，常年在外）多年未真正坐下来聊过。主线是亲情的和解与重聚。' `
    -tone '温暖克制的家庭叙事，中文：炊烟、菜园、旧相册与欲言又止。' `
    -directorExtra '以「做饭、翻旧物、修理、多年心结的坦白」等家庭场景推进；不煽情过度，让误会一层层解开。亲情线不需恋爱情节，保持克制与温度。玩家可自由行动或选择视角，关键节点给2-4个灵感。' -tokens 1000
# 职场 分支
$s3 = @(
    (Node 'start' 'narration' '加班夜' '凌晨的办公室只剩两盏灯。林越把一份重做的方案放到周谨桌上：「吃口东西再改。」周谨抬头，把餐盒往她那边推了推：「你也没吃吧？一起。」' '' '' @(
        (Choice '「行，那我说下方案思路。」' 'talk'),
        (Choice '先安静地吃完这顿饭' 'eat')
    )),
    (Node 'talk' 'narration' '一起改到天亮' '两人对着屏幕把方案推翻又重来，从较劲变成了少见的心有灵犀。窗外天快亮时，林越忽然发现，她很久没跟人这么默契过了。' '' '' @(
        (Choice '「以后……我都跟你一起改。」' 'end_partner'),
        (Choice '（把加班餐券默默放到他抽屉里）' 'end_partner')
    )),
    (Node 'eat' 'narration' '一碗夜宵' '那顿饭谁也没再提工作，只聊起各自入行时的一点执念。周谨说，他其实很怕辜负“信任”。林越没答话，只是把餐盒里最后一块肉夹给了他。' '' '' @(
        (Choice '「你不辜负就行——我信你。」' 'end_partner'),
        (Choice '（把一张票塞进他文件夹）' 'end_partner')
    )),
    (Node 'end_partner' 'ending' '结局 · 并肩的人' '后来他们仍是同事，也成了彼此最可靠的“搭档”。办公室的灯还是会亮到很晚，但多了一个愿意一起改方案、也愿意一起看日出的人。' )
)
$stories += Story -id 's-bare2-louti' -title '向上的楼梯' -subtitle '职场：从对手到搭档' -emoji '🏢' -color 0 -genre '职场 · 治愈 · 恋爱 · 分支' -charIds @('c-linyue','c-zhoujin') -nodes $s3 -start 'start' `
    -world '现代都市广告公司，创意部。林越（女，33，总监）与周谨（男，27，副总监）。全员成年、职场向。' `
    -tone '克制利落的职场叙事：方案、加班、夜宵与默契。' `
    -directorExtra '（纯分支剧本，可离线游玩；感情主线含蓄。）' -mode 'script' -temp 0.9 -tokens 650
$bundle=@{version=1; exportedAt=[long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()); providers=@(); characters=$chars; stories=$stories; saves=@()}
$dir='F:\OneDrive\Documents\harness\WenYouTextQuest\presets'; $out=Join-Path $dir 'wenyou-bare2-presets.json'
$json=$bundle | ConvertTo-Json -Depth 30
[System.IO.File]::WriteAllText($out,$json,(New-Object System.Text.UTF8Encoding($false)))
Write-Output "WROTE $out"
$ck=Get-Content $out -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Output ("characters="+$ck.characters.Count+" stories="+$ck.stories.Count)
$bad=0
foreach($st in $ck.stories){ $ids=@($st.nodes.PSObject.Properties.Name); foreach($p in $st.nodes.PSObject.Properties){$n=$p.Value; if($n.choices){foreach($c in $n.choices){ if($c.next -and $c.next -ne '@self' -and -not($ids -contains $c.next)){Write-Output ("BAD next "+$st.id+" "+$n.id+" -> "+$c.next);$bad++}}}}; if($ids -notcontains $st.startNodeId){Write-Output ("BAD start "+$st.id);$bad++} }
Write-Output ("link-check bad="+$bad)