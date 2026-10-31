param([string]$OutDir, [string]$OnlyRegion = "")
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Add-Type -TypeDefinition (Get-Content -Raw "$here\levelgen.cs") -Language CSharp

function L($region, $name, $restored, $hint, $accent, $leaf, $skyTop, $skyBottom, $shadowStep, $light, $hunterStep, $spec) {
    [pscustomobject]@{ Region=$region; Name=$name; Restored=$restored; Hint=$hint; Accent=$accent; Leaf=$leaf;
        SkyTop=$skyTop; SkyBottom=$skyBottom; ShadowStep=$shadowStep; Light=$light; HunterStep=$hunterStep; Spec=$spec }
}

$levels = @(
  # ------------------------------------------------ Orman 6-10
  (L forest "Mor Menekşeler" "Menekşeler mor mor açtı!" "Gölgeler ve dikenler bir arada. Yolunu önceden düşün." "FF9B6BFF" "FF3FA34D" "FF2B2350" "FFC9B3FF" 0.40 2.4 0.55 @{Theme="forest";Seed=601;Wall=0.30;Thorns=3;Shadows=1;MinMoves=30;MaxMoves=50}),
  (L forest "Pembe Kiraz" "Kiraz ağaçları pembe çiçeklendi!" "İki gölge ormanda nöbette. Ritimlerini izle." "FFFF8FB8" "FF4CAF50" "FF3A2340" "FFFFC1D6" 0.38 2.4 0.55 @{Theme="forest";Seed=702;Wall=0.32;Thorns=4;Shadows=2;MinMoves=32;MaxMoves=55}),
  (L forest "Altın Başaklar" "Başaklar altın sarısına döndü!" "Kapı kilitli. Önce kristali bul, sonra kapıdan geç." "FFFFC94A" "FF8BC34A" "FF3A2E1A" "FFFFE3A0" 0.38 2.3 0.55 @{Theme="forest";Seed=803;Wall=0.28;Thorns=3;Door=$true;Shadows=1;MinMoves=34;MaxMoves=60}),
  (L forest "Gümüş Çiy" "Çiy damlaları gümüş gibi parladı!" "Kapı, kristal ve iki gölge. Acele etme." "FFCFE3F0" "FF66BB6A" "FF1F2E44" "FFDDEFFF" 0.36 2.3 0.55 @{Theme="forest";Seed=904;Wall=0.30;Thorns=4;Door=$true;Shadows=2;MinMoves=38;MaxMoves=65}),
  (L forest "Orman Kalbi" "Ormanın kalbi yeniden yeşerdi!" "Ormanın son sınavı: üç gölge ve kilitli bir kapı." "FF6BE08A" "FF2E8B57" "FF163A2E" "FFB8F5C9" 0.34 2.2 0.55 @{Theme="forest";Seed=1005;Wall=0.30;Thorns=4;Door=$true;Shadows=3;MinMoves=40;MaxMoves=70}),

  # ------------------------------------------------ Göl 6-10
  (L lake "Gümüş Balıklar" "Balıklar gümüş pullarıyla parladı!" "Nilüfer köprülerini dikkatle seç; her biri bir kez taşır." "FF9FD3FF" "FF3FA36B" "FF1C2A50" "FFBFE4FF" 0.38 2.1 0.55 @{Theme="lake";Seed=1601;Tries=5000;MaxLilies=12;Wall=0.25;Island=0.24;Lily=0.32;Shadows=1;MinMoves=26;MaxMoves=55}),
  (L lake "Su Perisi" "Su perileri turkuaz dans etti!" "Bir adaya girdiğin nilüferden geri dönemezsin. Dönüş yolunu önceden gör." "FF7FE0D0" "FF26A69A" "FF123E4A" "FFA8F0E6" 0.36 2.0 0.55 @{Theme="lake";Seed=1702;Tries=5000;MaxLilies=12;Wall=0.25;Island=0.24;Lily=0.32;RequireDiff=$true;Shadows=1;MinMoves=26;MaxMoves=60}),
  (L lake "Kamış Labirenti" "Kamışlar limon yeşiline büründü!" "Kristal suyun ortasında, kapı karşı kıyıda. Nilüferlerini idareli kullan." "FFD4E157" "FF7CB342" "FF24402A" "FFE6F5A8" 0.36 2.0 0.55 @{Theme="lake";Seed=1803;Tries=5000;MaxLilies=12;Wall=0.25;Island=0.24;Lily=0.32;Door=$true;Shadows=1;MinMoves=28;MaxMoves=60}),
  (L lake "Yakamoz" "Göl yakamozla mavi mavi ışıldadı!" "Sis yoğun, gölgeler hızlı. Her nilüfer tek kullanımlık." "FF64B5F6" "FF5E7CE2" "FF0F2246" "FFA9D4FF" 0.34 1.9 0.55 @{Theme="lake";Seed=1904;Tries=25000;MaxLilies=12;Wall=0.25;Island=0.24;Lily=0.32;RequireDiff=$true;Door=$true;Shadows=1;MinMoves=26;MaxMoves=65}),
  (L lake "Ay Gölü" "Ay gölü leylak rengine boyandı!" "Gölün son sınavı: kristal, kapı, gölge ve tek kullanımlık köprüler." "FFE1BEE7" "FF9575CD" "FF2A1F5C" "FFE8D5F5" 0.32 1.9 0.55 @{Theme="lake";Seed=2005;Tries=30000;MaxLilies=12;Wall=0.22;Island=0.24;Lily=0.32;RequireDiff=$true;Door=$true;Shadows=1;MinMoves=26;MaxMoves=70}),

  # ------------------------------------------------ Mağara 6-10
  (L cave "Ametist Mağarası" "Ametistler mor ışıkla parladı!" "İki avcı mağaranın derinliklerinde uyuyor. Uyandırırsan kaç!" "FFB388FF" "FF9C6ADE" "FF1E1038" "FFD9C2FF" 0.32 1.7 0.48 @{Theme="cave";Seed=2601;Tries=8000;Wall=0.28;Thorns=4;Door=$true;Shadows=2;Hunters=1;MinMoves=34;MaxMoves=65}),
  (L cave "Lav Damarı" "Lav damarları turuncu parladı!" "Dikenler her yerde, ışık çok dar. Dokunarak yürümek daha güvenli." "FFFF7043" "FFE0795A" "FF2A1010" "FFFFC2A0" 0.30 1.6 0.46 @{Theme="cave";Seed=2702;Wall=0.30;Thorns=6;Door=$true;Shadows=2;Hunters=1;MinMoves=40;MaxMoves=70}),
  (L cave "Yankılı Kuyu" "Kuyunun dibi camgöbeği ışıkla doldu!" "İki avcı, iki gölge. Avcıları birbirinden uzak tut." "FF4DD0E1" "FF26C6DA" "FF0C2230" "FFA8EEF5" 0.30 1.6 0.45 @{Theme="cave";Seed=2803;Wall=0.30;Thorns=4;Door=$true;Shadows=2;Hunters=2;MinMoves=40;MaxMoves=70}),
  (L cave "Zümrüt Damla" "Zümrüt damlaları yeşil parladı!" "Üç gölge ve bir avcı dar tünellerde. Ritmi yakala." "FF43E08A" "FF2E9E6B" "FF0E2A1E" "FFB0F5D0" 0.28 1.5 0.44 @{Theme="cave";Seed=2904;Tries=6000;Wall=0.27;Thorns=4;Door=$true;Shadows=3;Hunters=1;MinMoves=34;MaxMoves=72}),
  (L cave "Derin Kalp" "Mağaranın derin kalbi altın gibi atıyor!" "Mağaranın son sınavı: üç gölge, iki avcı ve en dar ışık." "FFFFAB40" "FFE0A040" "FF241A0A" "FFFFDDA8" 0.28 1.5 0.42 @{Theme="cave";Seed=3005;Tries=6000;Wall=0.27;Thorns=4;Door=$true;Shadows=3;Hunters=2;MinMoves=34;MaxMoves=75}),

  # ------------------------------------------------ Karlı Dağ 1-10
  (L snow "İlk Kar" "İlk kar beyaz beyaz parladı!" "Buza basınca bir engele çarpana kadar kayarsın. Kayalara çarparak dur." "FFE3F2FD" "FF90CAF9" "FF1B2A4A" "FFDDEBFF" 0.42 2.4 0.55 @{Theme="snow";Seed=3101;Ice=0.42;Wall=0.14;Thorns=0;MinForced=4;MinMoves=8;MaxMoves=18}),
  (L snow "Buz Pateni" "Buz pisti açık maviye büründü!" "Kaymanın nerede biteceğini önceden kestir. Bazen ters yönden yaklaşman gerekir." "FF81D4FA" "FF4FC3F7" "FF162846" "FFBDE8FF" 0.42 2.3 0.55 @{Theme="snow";Seed=3202;Ice=0.46;Wall=0.14;Thorns=1;MinForced=6;MinMoves=10;MaxMoves=22}),
  (L snow "Kristal Saçak" "Buz saçakları kristal gibi ışıldadı!" "Buzda dikene kayarsan ışığın söner. Kaymadan önce yolu izle." "FFB3E5FC" "FF81D4FA" "FF13243F" "FFD6F2FF" 0.40 2.3 0.55 @{Theme="snow";Seed=3303;Ice=0.46;Wall=0.13;Thorns=2;Shadows=1;MinForced=7;MinMoves=10;MaxMoves=24}),
  (L snow "Kar Tanesi" "Kar taneleri pembe pembe süzüldü!" "Uzun buz yolları ve bir gölge. Durabileceğin kayaları ara." "FFF8BBD0" "FFB0BEC5" "FF2A2440" "FFFFD9E6" 0.40 2.2 0.55 @{Theme="snow";Seed=3404;Ice=0.5;Wall=0.13;Thorns=2;Shadows=1;MinForced=8;MinMoves=12;MaxMoves=26}),
  (L snow "Kuzey Işığı" "Gökyüzünde yeşil kuzey ışıkları dalgalandı!" "İki gölge karda nöbette. Kaymaya başlamadan önce zamanını iyi seç." "FF69F0AE" "FF4DB6AC" "FF0E2236" "FFB9F6CA" 0.38 2.2 0.55 @{Theme="snow";Seed=3505;Ice=0.48;Wall=0.13;Thorns=2;Shadows=2;MinForced=9;MinMoves=12;MaxMoves=28}),
  (L snow "Donmuş Şelale" "Donmuş şelale masmavi parladı!" "Kristal kapıyı açar. Kristale kayarak ulaşman gerekebilir." "FF4FC3F7" "FF29B6F6" "FF0D2140" "FFAEE3FF" 0.38 2.1 0.55 @{Theme="snow";Seed=3606;Ice=0.46;Wall=0.13;Thorns=2;Door=$true;Shadows=1;MinForced=9;MinMoves=14;MaxMoves=30}),
  (L snow "Tipi" "Tipi dindi, dağ gümüşe büründü!" "Tipi ışığını daraltıyor. Buz, dikenler ve iki gölge." "FFCFD8DC" "FF90A4AE" "FF1C2430" "FFE3EBF0" 0.36 1.9 0.55 @{Theme="snow";Seed=3707;Ice=0.5;Wall=0.13;Thorns=3;Door=$true;Shadows=2;MinForced=10;MinMoves=14;MaxMoves=30}),
  (L snow "Buz Sarayı" "Buz sarayı turkuaz ışıkla doldu!" "Sarayın koridorları kaygan, kapısı kilitli. Her kaymayı planla." "FF80DEEA" "FF4DD0E1" "FF0C2A33" "FFC5F5FA" 0.36 2.0 0.55 @{Theme="snow";Seed=3808;Ice=0.52;Wall=0.13;Thorns=3;Door=$true;Shadows=2;MinForced=11;MinMoves=15;MaxMoves=32}),
  (L snow "Zirve Yolu" "Zirve yolu eflatun şafakla aydınlandı!" "Bir avcı zirvede uyuyor. Kaygan zeminde ondan kaçmak zor!" "FFB39DDB" "FF9575CD" "FF1F1838" "FFDCCFF5" 0.34 1.9 0.5 @{Theme="snow";Seed=3909;Ice=0.5;Wall=0.13;Thorns=3;Door=$true;Shadows=2;Hunters=1;MinForced=12;MinMoves=15;MaxMoves=34}),
  (L snow "Kar Kraliçesi" "Kar Kraliçesi'nin tacı pembe ışıkla parladı!" "Dağın son sınavı: buz, kapı, gölgeler ve avcı." "FFF48FB1" "FFCE93D8" "FF2A1A38" "FFFFD1E3" 0.32 1.9 0.48 @{Theme="snow";Seed=4010;Ice=0.52;Wall=0.13;Thorns=3;Door=$true;Shadows=2;Hunters=1;MinForced=13;MinMoves=16;MaxMoves=36}),

  # ------------------------------------------------ Bulut Bahçesi 1-10
  (L cloud "Pamuk Bulutlar" "Bulutlar pamuk gibi sarı parladı!" "Rüzgâr okları seni gösterdiği yöne iter. Boşluğa adım atamazsın." "FFFFF59D" "FFFFE082" "FF2B2A5C" "FFFFF4C2" 0.42 2.6 0.55 @{Theme="cloud";Seed=4101;Void=0.18;Wind=0.12;Ice=0.0;Wall=0.08;Thorns=0;MinForced=3;MinMoves=10;MaxMoves=22}),
  (L cloud "Rüzgâr Gülü" "Rüzgâr gülü şeftali rengiyle döndü!" "Rüzgârlar birbirine bağlanabilir. Zincirin nerede biteceğini izle." "FFFFCC80" "FFFFB74D" "FF3A2A50" "FFFFE0B2" 0.40 2.5 0.55 @{Theme="cloud";Seed=4202;Void=0.2;Wind=0.14;Ice=0.0;Wall=0.08;Thorns=1;Shadows=1;MinForced=4;MinMoves=12;MaxMoves=26}),
  (L cloud "Gün Batımı" "Bulutlar gün batımı turuncusuna boyandı!" "Buz ve rüzgâr bir arada. Kayarken rüzgâra girersen yön değiştirirsin." "FFFF8A65" "FFFF7043" "FF3A1E40" "FFFFC2A8" 0.40 2.4 0.55 @{Theme="cloud";Seed=4303;Void=0.2;Wind=0.12;Ice=0.12;Wall=0.08;Thorns=1;Shadows=1;MinForced=6;MinMoves=12;MaxMoves=28}),
  (L cloud "Uçan Adalar" "Uçan adalar yemyeşil oldu!" "Adalar arası rüzgârlarla geçilir. İki gölge yolları gözlüyor." "FFA5D6A7" "FF66BB6A" "FF1E2E40" "FFD0F0D2" 0.38 2.4 0.55 @{Theme="cloud";Seed=4404;Void=0.24;Wind=0.14;Ice=0.08;Wall=0.08;Thorns=2;Shadows=2;MinForced=6;MinMoves=14;MaxMoves=30}),
  (L cloud "Gökkuşağı Köprüsü" "Gökkuşağı köprüsü pembe ışıkla yayıldı!" "Kristal bir adada, kapı ötekinde. Rüzgârı arkana al." "FFF06292" "FFBA68C8" "FF2E1A44" "FFFFC1DA" 0.36 2.3 0.55 @{Theme="cloud";Seed=4505;Void=0.22;Wind=0.13;Ice=0.08;Wall=0.08;Thorns=2;Door=$true;Shadows=1;MinForced=7;MinMoves=14;MaxMoves=32}),
  (L cloud "Yıldız Tozu" "Yıldız tozu altın gibi saçıldı!" "Dikenler, kaygan bulutlar ve iki gölge. Her itişi hesapla." "FFFFE082" "FFFFCA28" "FF1E1A44" "FFFFF0C2" 0.34 2.2 0.55 @{Theme="cloud";Seed=4606;Void=0.2;Wind=0.13;Ice=0.12;Wall=0.08;Thorns=3;Door=$true;Shadows=2;MinForced=8;MinMoves=15;MaxMoves=34}),
  (L cloud "Fırtına Gözü" "Fırtına dindi, bulutlar gümüşe döndü!" "Fırtınada bir avcı uyuyor. Rüzgâr seni ona doğru itmesin!" "FF90A4AE" "FF78909C" "FF161C2A" "FFD5DEE3" 0.34 2.1 0.5 @{Theme="cloud";Seed=4707;Void=0.2;Wind=0.15;Ice=0.1;Wall=0.08;Thorns=3;Door=$true;Shadows=2;Hunters=1;MinForced=8;MinMoves=15;MaxMoves=36}),
  (L cloud "Şimşek Bahçesi" "Şimşekler bahçeyi limon sarısına boyadı!" "Üç gölge ve bir avcı. Rüzgârla kaçış yollarını kullan." "FFFFF176" "FFDCE775" "FF1A1A3A" "FFFFF9C4" 0.32 2.1 0.48 @{Theme="cloud";Seed=4808;Tries=10000;Void=0.16;Wind=0.13;Ice=0.1;Wall=0.06;Thorns=2;Door=$true;Shadows=3;Hunters=1;MinForced=8;MinMoves=18;MaxMoves=40}),
  (L cloud "Şafak" "Şafak sökerken bulutlar mercan rengine döndü!" "İki avcı, iki gölge ve uzun rüzgâr zincirleri." "FFFFAB91" "FFFF8A65" "FF2A1830" "FFFFD7C8" 0.32 2.0 0.46 @{Theme="cloud";Seed=4909;Tries=10000;Void=0.16;Wind=0.14;Ice=0.1;Wall=0.06;Thorns=2;Door=$true;Shadows=2;Hunters=2;MinForced=8;MinMoves=20;MaxMoves=42}),
  (L cloud "Işığın Evi" "Bütün renkler Işığın Evi'nde buluştu!" "Son bölüm: öğrendiğin her şey burada. Işığın seninle!" "FFFFD54A" "FFFF7EB6" "FF1B1F5C" "FFFFD1A8" 0.30 2.0 0.45 @{Theme="cloud";Seed=5010;Tries=12000;Void=0.16;Wind=0.15;Ice=0.12;Wall=0.06;Thorns=3;Door=$true;Shadows=3;Hunters=2;MinForced=10;MinMoves=28;MaxMoves=46})
)

$out = @{}
foreach ($lv in $levels) {
    if ($OnlyRegion -and $lv.Region -ne $OnlyRegion) { continue }
    $s = New-Object Spec
    foreach ($k in $lv.Spec.Keys) { $s.$k = $lv.Spec[$k] }
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $r = [LevelGen]::Make($s)
    if ($r -eq $null) { Write-Host ("FAILED  {0}" -f $lv.Name); continue }
    Write-Host ("{0,-20} moves={1,2} forced={2,2} diff={3} states={4,6}  ({5:n1}s)" -f $lv.Name, $r.Moves, $r.Forced, $r.Diff, $r.States, $sw.Elapsed.TotalSeconds)
    foreach ($row in $r.Map) { Write-Host "        $row" }
    $hex = { param($c) "0x$c.toInt()" }
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("        LevelDef(")
    [void]$sb.AppendLine("            name = `"$($lv.Name)`",")
    [void]$sb.AppendLine("            restoredText = `"$($lv.Restored)`",")
    [void]$sb.AppendLine("            hint = `"$($lv.Hint)`",")
    [void]$sb.AppendLine("            accent = $(& $hex $lv.Accent),")
    [void]$sb.AppendLine("            leaf = $(& $hex $lv.Leaf),")
    [void]$sb.AppendLine("            skyTop = $(& $hex $lv.SkyTop),")
    [void]$sb.AppendLine("            skyBottom = $(& $hex $lv.SkyBottom),")
    [void]$sb.AppendLine(("            shadowStep = {0}f," -f ([double]$lv.ShadowStep).ToString([Globalization.CultureInfo]::InvariantCulture)))
    [void]$sb.AppendLine(("            light = {0}f," -f ([double]$lv.Light).ToString([Globalization.CultureInfo]::InvariantCulture)))
    [void]$sb.AppendLine(("            hunterStep = {0}f," -f ([double]$lv.HunterStep).ToString([Globalization.CultureInfo]::InvariantCulture)))
    [void]$sb.AppendLine("            map = listOf(")
    foreach ($row in $r.Map) { [void]$sb.AppendLine("                `"$row`",") }
    [void]$sb.AppendLine("            ),")
    [void]$sb.AppendLine("        ),")
    if (-not $out.ContainsKey($lv.Region)) { $out[$lv.Region] = New-Object System.Text.StringBuilder }
    [void]$out[$lv.Region].Append($sb.ToString())
}
foreach ($k in $out.Keys) {
    [IO.File]::WriteAllText("$OutDir\gen_$k.kt", $out[$k].ToString(), (New-Object System.Text.UTF8Encoding($false)))
}
