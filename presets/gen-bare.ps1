$ErrorActionPreference = 'Stop'

$chars = @(
    @{ id = 'c-guyanshen'; name = '顾言深'; emoji = '🕵️'; colorIndex = 0; tagline = '雾都市刑警队长：冷面但不冷血'
       personality = '逻辑缜密、话少果断，对下属严厉却护短；习惯把情绪压在证据底下。'
       speechStyle = '短句、干练；审案时一针见血，私下里会突然冒出一点冷幽默。'
       background = '32岁，雾都市公安局刑侦大队队长，破过十几桩重案；最怕案情里出现想救却救不了的普通人。'
       exampleDialogue = '「证据不会说谎，人才会。」'
       greeting = '「又来案子了。走，现场。」' }
    @{ id = 'c-luwan'; name = '陆晚'; emoji = '🔬'; colorIndex = 8; tagline = '法医：用细节比真相更冷静'
       personality = '理性、细致、有点慢热；面对尸检冷静，面对人情却容易心软。'
       speechStyle = '专业、平和，术语之外爱用比喻。'
       background = '28岁，市局法医，和顾言深搭档多年；抽屉里常年备着一盒没送出去的巧克力。'
       exampleDialogue = '「死亡时间推后了六个小时——有人动过现场。」'
       greeting = '「报告出来了，你最好先看一眼。」' }
    @{ id = 'c-xiaoche'; name = '萧彻'; emoji = '🗡️'; colorIndex = 3; tagline = '落魄剑客：江湖是旧梦，酒馆是归途'
       personality = '表面懒散、嗜酒，实则一身傲骨；重情重义，欠下的恩必还。'
       speechStyle = '懒洋洋、话里带刺，关键时刻却字字千斤。'
       background = '30岁，曾在武林掀起风波又悄然隐退，如今在边城守着妹妹留下的酒馆。'
       exampleDialogue = '「酒我请你，话你少说。」'
       greeting = '「今日打烊……不过你若是来喝一碗，我破例。」' }
    @{ id = 'c-ali'; name = '阿璃'; emoji = '🍶'; colorIndex = 2; tagline = '酒馆老板娘：嘴硬心软，账本比刀快'
       personality = '爽利泼辣、算账精明；刀子嘴豆腐心，最见不得落魄的人饿肚子。'
       speechStyle = '大嗓门、爱呛人，关心的话总藏在大声里。'
       background = '26岁，边城「听风酒馆」老板娘，守着妹妹和一间老铺子，也守着一个不告而别的剑客。'
       exampleDialogue = '「酒钱记你账上！……人平安回来就行。」'
       greeting = '「又要赊账？进来坐。」' }
    @{ id = 'c-shenyi'; name = '沈亦'; emoji = '🚀'; colorIndex = 1; tagline = '星舰领航员：负责带所有人回家'
       personality = '沉稳冷静、决策果断；在陌生星域里永远先算逃生路线，再谈理想。'
       speechStyle = '简洁、专业，偶尔用数据开一句玩笑。'
       background = '29岁，深空勘探舰「回声号」领航员，曾在一场事故中独自活着返航。'
       exampleDialogue = '「优先保护船员，机器可以再造。」'
       greeting = '「跃迁窗口还有四小时，够你交代后事了。」' }
    @{ id = 'c-bailu'; name = '白露'; emoji = '🛠️'; colorIndex = 6; tagline = '舰船工程师：把不可能修成可能'
       personality = '外向、动手派、好奇心旺盛；遇到难题眼睛会发亮。'
       speechStyle = '语速快、爱连珠炮式提问。'
       background = '26岁，回声号首席工程师，和沈亦是老搭档；工具箱里存着两张没寄出去的船票。'
       exampleDialogue = '「给我一晚，我能让二段引擎重新点火。」'
       greeting = '「你又偷偷改航线了？先说好，我可没带备用伞。」' }
    @{ id = 'c-helan'; name = '贺阑'; emoji = '🏗️'; colorIndex = 5; tagline = '青年建筑师：理性造楼，笨拙爱人'
       personality = '严谨克制、不擅表达；习惯把在乎做成一件件具体的事。'
       speechStyle = '正经、偶尔发僵；被夸奖会脸红。'
       background = '29岁，建筑事务所主创，租了一间采光极好的客厅，和一个抢他厨房的插画师。'
       exampleDialogue = '「我把阳台改成了书桌……这样你画画不会背光。」'
       greeting = '「下班了？我把饭热一下。」' }
    @{ id = 'c-qiaoyi'; name = '乔一'; emoji = '🎨'; colorIndex = 4; tagline = '自由插画师：脑子天马行空，胃却很诚实'
       personality = '活泼跳脱、灵感派；嘴上没把门，心里却细，记得每个人爱吃的菜。'
       speechStyle = '语速快、爱想象；正经时很温柔。'
       background = '25岁，在家接稿的自由插画师，和建筑师贺阑从「合租」开始，把日子画成了连载。'
       exampleDialogue = '「我把咱俩画进新绘本了……你怎么脸红了？」'
       greeting = '「今天想吃什么？我来点。」' }
)

function Node([string]$id,[string]$kind,[string]$title,[string]$text,[string]$speaker='',[string]$prompt='',$choices=@(),$onEnter=@(),[string]$endTarget='') {
    $o=@{id=$id;kind=$kind;title=$title;text=$text}; if($speaker){$o.speakerId=$speaker}; if($prompt){$o.prompt=$prompt}; $o.choices=$choices; $o.onEnter=$onEnter; if($endTarget){$o.endTarget=$endTarget}; return $o
}
function Choice([string]$text,[string]$next,$conds=@(),$effects=@()){ return @{text=$text;next=$next;conditions=$conds;effects=$effects;hint=''} }
function Prologue([string]$text){ return @( (Node 'start' 'narration' '开场' $text) ) }
function Story([string]$id,[string]$title,[string]$subtitle,[string]$emoji,[int]$color,[string]$genre,$charIds,$nodes,[string]$start,[string]$world,[string]$tone,[string]$directorExtra,[double]$temp=0.95,[int]$tokens=1000,[string]$mode='ai_dm'){
    $nm=@{}; foreach($n in $nodes){$nm[$n.id]=$n}
    $ai=@{worldSummary=$world;tone=$tone;directorExtra=$directorExtra;temperature=$temp;maxTokens=$tokens;historyWindow=40}
    return @{id=$id;title=$title;subtitle=$subtitle;coverEmoji=$emoji;colorIndex=$color;genre=$genre;mode=$mode;characterIds=$charIds;startNodeId=$start;nodes=$nm;initialVariables=@{};initialFlags=@();ai=$ai}
}

$stories=@()

# 1) 都市悬疑 AI导演
$stories += Story -id 's-bare-wudu' -title '雾都档案' -subtitle '他在等一个能对上的细节，她在等一句真话' -emoji '🌫️' -color 0 -genre '都市 · 刑侦 · 悬疑 · 言情' -charIds @('c-guyanshen','c-luwan') -nodes (Prologue '雾都入冬，一桩地铁站里的离奇案子把顾言深和陆晚再一次绑在了深夜的现场。他们配合多年，一个看人，一个看物证，唯独谁也没拆穿彼此那点没说出口的在意。你可以扮演其中一方，自由输入下一步，把案子与心事一起推进。') -start 'start' `
    -world '现代都市雾都，连续发生几起表面互不相干、内里却环环相扣的案件。刑警队长顾言深（32，男）与法医陆晚（28，女）搭档破案。故事以「案件推进+两人关系缓进」双线并行，基调克制而带暖意。全员成年。' `
    -tone '冷色调刑侦氛围配一点人情冷暖，中文：深夜现场、化验室、雨巷与没拆的巧克力。' `
    -directorExtra '请双线并行：每个案件中给出可调查的分支（物证、证人、动机），并让顾言深与陆晚在对峙与配合中慢慢靠近；感情线含蓄，允许表白与并肩，但不写露骨亲密。玩家可自由行动或选择任一视角，关键节点给2-4个灵感。' -tokens 1050

# 2) 古风武侠 AI导演
$stories += Story -id 's-bare-jianhu' -title '剑与酒馆' -subtitle '刀光之外，还有一碗热酒' -emoji '🏮' -color 3 -genre '古风 · 武侠 · 情感' -charIds @('c-xiaoche','c-ali') -nodes (Prologue '边城的夜里，听风酒馆挂着灯。落魄剑客萧彻靠在柜台擦剑，老板娘阿璃一边算账一边呛他「我这儿不养闲人」。旧敌寻仇、江湖风起，他们之间的那点纠葛也终于要有个交代。你可以扮演其中一方，自由输入下一步。') -start 'start' `
    -world '虚构古风边城：听风酒馆是往来江湖人的落脚点。萧彻（30，男，隐退剑客）与阿璃（26，女，酒馆老板娘）守着彼此和昔日的秘密。故事以江湖恩怨为主线，感情线武侠化、克制。' `
    -tone '古风武侠的苍凉与温柔，中文：大漠、灯影、剑鸣、酒碗。' `
    -directorExtra '主线是旧案与追杀带来的江湖张力，情感线随共患难推进；萧彻的身世与阿璃的旧约可成为反复揭开的钩子。保持人物的傲骨与侠义，亲密点到为止。玩家可自由行动或选择视角，关键节点给2-4个灵感。' -tokens 1050

# 3) 科幻 AI导演
$stories += Story -id 's-bare-xingchen' -title '星尘回响' -subtitle '在荒芜星域里，他们互为坐标' -emoji '🚀' -color 1 -genre '科幻 · 太空 · 冒险 · 情感' -charIds @('c-shenyi','c-bailu') -nodes (Prologue '深空勘探舰「回声号」失去与地球的联系，飘向一片从未标注的星域。领航员沈亦负责带所有人回家，工程师白露一次次把瘫痪的系统救回来。黑暗里，他们把彼此当成了坐标。你可以扮演其中一方，自由输入下一步。') -start 'start' `
    -world '近未来深空探索：回声号在未知星域失联，燃料有限、信号不明。沈亦（29，男，领航员）与白露（26，女，工程师）相互支撑；主线是生存与返航，感情线在危机中沉淀。' `
    -tone '冷静克制的科幻叙事，中文：舷窗星光、引擎低鸣、应急灯下的对话。' `
    -directorExtra '以生存难题（能源、故障、未知信号、抉择）推动剧情，并在资源抉择里让两个人逐渐坦诚；可以有一次「谁留下/谁先走」的高价值选择。感情含蓄浪漫，无露骨描写。玩家可自由行动或选择视角。' -tokens 1050

# 4) 都市言情 分支
$s4 = @(
    (Node 'start' 'narration' '合租第一天' '贺阑搬进新公寓时，撞见的不是空客厅，而是一个正把画笔往墙上甩的姑娘——乔一。她回头眨眨眼：「你就是我那个合租室友？我把阳台改成画室了，你没意见吧？」' '' '' @(
        (Choice '「……有意见，但可以先听你说。」' 'talk'),
        (Choice '「你改吧，但厨房归我。」' 'kitchen')
    )),
    (Node 'talk' 'narration' '深夜对话' '他们在客厅就着外卖聊到凌晨。贺阑不擅表达，却记得乔一说过的每一句喜欢的颜色；乔一嘴上疯，却悄悄把他掉的图纸全捡起来收好。' '' '' @(
        (Choice '「要不要……一起住久一点？」' 'stay'),
        (Choice '先写一张纸条塞进她门缝' 'note')
    )),
    (Node 'kitchen' 'narration' '一桌家常' '两人约法三章：他做饭，她收拾。后来谁也没想到，最普通的一日三餐，会长成最舍不得放下的日常。' '' '' @(
        (Choice '（顺理成章地）「今天吃什么？」' 'stay'),
        (Choice '「阳台借我晒两件衣服？」' 'note')
    )),
    (Node 'stay' 'narration' '一起走下去' '那天之后，合租合同续了一次又一次。他把阳台改成了她的书桌，她把他画进了每一本新绘本——封面常常是他背光的侧脸。' '' '' @(
        (Choice '「遇见你，是我最得意的改造。」' 'end_couple'),
        (Choice '（把钥匙递给她）：「当家吧。」' 'end_couple')
    )),
    (Node 'note' 'narration' '一张纸条' '贺阑终于学会把她想说又没说出口的话写下来。乔一看到那张字条时，厨房正咕嘟着一锅汤，她红着眼眶喊：「你字真丑——但意思我收到了。」' '' '' @(
        (Choice '「那以后都由我来写。」' 'end_couple')
    )),
    (Node 'end_couple' 'ending' '结局 · 同租合约' '窗台的绿植、锅里的汤、墙上的画、还有书桌上并排的两盏灯——他们没签下房子，却把「家」签了下来。' )
)
$stories += Story -id 's-bare-tongzu' -title '同租合约' -subtitle '都市言情分支：从合租到把日子过成一家人' -emoji '🏠' -color 4 -genre '都市 · 恋爱 · 日常 · 分支' -charIds @('c-helan','c-qiaoyi') -nodes $s4 -start 'start' `
    -world '现代都市，一分享租的公寓与两个人。全员成年。' `
    -tone '温馨轻快，日常系对白。' `
    -directorExtra '（纯分支剧本，可离线游玩。）' -mode 'script' -temp 0.9 -tokens 650

$bundle=@{version=1; exportedAt=[long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()); providers=@(); characters=$chars; stories=$stories; saves=@()}
$dir='F:\OneDrive\Documents\harness\WenYouTextQuest\presets'
$out=Join-Path $dir 'wenyou-bare-presets.json'
$json=$bundle | ConvertTo-Json -Depth 30
[System.IO.File]::WriteAllText($out,$json,(New-Object System.Text.UTF8Encoding($false)))
Write-Output "WROTE $out"
$ck=Get-Content $out -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Output ("characters="+$ck.characters.Count+" stories="+$ck.stories.Count)
$bad=0
foreach($st in $ck.stories){ $ids=@($st.nodes.PSObject.Properties.Name); foreach($p in $st.nodes.PSObject.Properties){$n=$p.Value; if($n.choices){foreach($c in $n.choices){ if($c.next -and $c.next -ne '@self' -and -not($ids -contains $c.next)){Write-Output ("BAD next story="+$st.id+" node="+$n.id+" next="+$c.next);$bad++}}}}; if($ids -notcontains $st.startNodeId){Write-Output ("BAD start "+$st.id);$bad++} }
Write-Output ("link-check bad="+$bad)