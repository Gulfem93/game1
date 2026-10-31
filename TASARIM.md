# Lumi: Kayıp Renkler — Oyun Tasarım Belgesi (v1)

## 1. Özet

| | |
|---|---|
| **Tür** | Kare tabanlı bulmaca / keşif |
| **Platform** | Android (telefon, dikey ekran), minSdk 26 |
| **Oturum süresi** | Bölüm başına 30–90 saniye |
| **Hedef kitle** | 8+ yaş, sakin ve kısa oyun arayan herkes |
| **Kapsam (v1)** | 50 bölüm (5 bölge), 4 ekran, çevrim dışı, reklamsız, üyeliksiz |

Bütün renkleri kaybolmuş bir ormanda, küçük ışık ruhu **Lumi** karanlık karelerde
ilerleyip **3 ışık parçası** toplar. Enerji dolunca bölümün sonundaki renk fidanı parlar;
Lumi fidana ulaştığında fidandan bir renk dalgası yayılır ve orman o rengi geri kazanır.

**Ayırt edici fikir:** Işık hem görüş alanıdır hem de bulmaca aracıdır.
Lumi'nin ışık çemberi topladığı her parçayla büyür. Mavi kristale dokunan ışık kapıları açar.

## 2. Temel döngü

```
Bölüme gir → karanlıkta keşfet → 3 ışık parçasını topla (enerji 3/3)
→ fidan parlar → fidana ulaş → renk dalgası → ★ kazan → sonraki bölüm açılır
```

## 3. Mekanikler

### 3.1 Hareket
- Harita **7 × 10** kareden oluşur.
- **Bir kareye dokun:** Lumi en kısa yoldan oraya yürür. Yol bulma ağaçlardan,
  kapalı kapılardan ve dikenlerden kaçınır.
- **Kaydır:** Lumi o yöne tek kare ilerler. Bu modda dikenlerden kaçınılmaz, dikkatli ol.
- Lumi bir kareyi 0,16 saniyede geçer. Hareket her zaman kareden kareye olur, serbest fizik yoktur.

### 3.2 Işık ve görüş
- Ekran karanlıktır. Lumi'nin çevresinde yumuşak bir ışık çemberi vardır.
- Işık yarıçapı: `taban + 0,55 × toplanan parça` kare. Taban ışık ormanda 2,4'tür,
  sisli göl bölümlerinde 2,0'a, mağarada 1,9'dan 1,7'ye kadar iner.
- Işığın yakınındaki kareler hafifçe renklenir. Bu, tam rengin ne olacağına dair küçük bir ipucudur.
- Işık parçaları, kristaller ve gölgelerin gözleri karanlıkta da parlar. Böylece oyun adil kalır.

### 3.3 Nesneler

| Simge | Nesne | Davranış |
|---|---|---|
| `#` | Duvar | Geçilemez. Ormanda ağaç, gölde kamış öbeği, mağarada kayadır |
| `~` | Su (göl) | Geçilemez |
| `o` | Nilüfer (göl) | Bir kez basılır; Lumi üstünden ayrılınca batar ve suya döner |
| `=` | Buz (karlı dağ) | Üstüne gelen Lumi aynı yönde, bir engele çarpana kadar kayar |
| `>` `<` `n` `u` | Rüzgâr (bulutlar) | Lumi'yi sağa, sola, yukarı veya aşağı iter; rüzgârlar zincirlenebilir |
| `A` | Avcı gölge (mağara) | Uyur. Lumi 4 adım yaklaşınca uyanır, en kısa yoldan kovalar; 7 adımdan fazla uzaklaşırsa durur |
| `*` | Işık parçası | Topla → enerji +1, ışık büyür |
| `E` | Renk fidanı | 3/3 enerjiyle parlar, üstüne gelince bölüm biter |
| `^` | Karanlık diken | Üstüne basarsan ışığın söner ve bölüm yeniden başlar |
| `H` / `V` | Gölge | Yatay/dikey devriye gezer, değerse ışık söner |
| `C` | Mavi kristal | Dokununca bütün kapılar açılır |
| `D` | Kapı | Kristal etkinleşene kadar kapalıdır |
| `L` | Başlangıç | Lumi'nin doğduğu kare |
| `?` | Gizli ışık parçası | Pıtır "Göster" diyene kadar görünmez ve toplanamaz |
| `:` | Görünmez köprü | Pıtır "Göster" diyene kadar su/boşluktur, sonra üstünden geçilir |
| `P` | Solmuş çiçek | Lumi giremez; Lumi yanına gelince Pıtır renk verir (Renk Dokunuşu) |
| `G` | Kök kapı | Çiçek açılana kadar kapalıdır |

### 3.3b Canavarlar
Haritadaki `H`, `V` (devriye) ve `A` (uyuyan avcı) bölgeye göre farklı canavarlara dönüşür. Her canavarın tek,
kolay anlaşılır bir farkı vardır; karmaşık özel güçleri yoktur. Bir türle ilk karşılaşmada bölümün başında
"Yeni canavar" kartı çıkar (görsel, ne yaptığı, ipucu). Görseller `res/drawable-nodpi/mon_*.png`; saydam zeminli
son hâlleri gelince değiştirilecek.

| Canavar | Nerede | Farkı |
|---|---|---|
| Gölge | Orman (`H`, ilk 5 bölümde `V` de) | Normal hızda devriye |
| Çiçek Canavarı | Orman 6–10 (`V`) | Çok yavaş devriye (adım süresi ×1,6) |
| Su Ruhu | Göl | Hızlı devriye (×0,8) |
| Kaya Yengeci | Mağara | Her dönüşte 0,6 sn bekler |
| Taş Golemi | Mağara, Karlı Dağ (`A`) | 5 adımdan uyanır ama yavaş kovalar (×1,4) |
| Ateş Böceği | Karlı Dağ | Hızlı (×0,75), karanlıkta parlar |
| Karanlık Kuş | Bulut | Çok hızlı (×0,7) |
| Renk Solucanı | Bulut (`A`) | Uyanınca 9 adım uzaklaşana kadar kovalar |

Canavarlar haritayı değiştirmez; çözücü onları hesaba katmaz, par değerleri değişmez.
Bölüm sonu canavarları (Solmuş Kadim, Gece Hükümdarı, Renk Yutan) henüz oyunda değildir.

### 3.4 Yoldaş: Pıtır
- 3. bölümde tanışılır. Lumi'yi bir kare geriden izler (Lumi'nin bir önceki karesinde durur).
- **Yol gösterme:** Pıtır'a dokununca ya da 15 saniye hamle yapılmayınca en yakın ulaşılabilir
  ışık parçasını (yoksa kristali, enerji doluysa fidanı) kesik bir ışık çizgisiyle gösterir.
  Bölümde 1 ipucu hakkı vardır, bağ 3. seviyedeyse 2.
- **Tepkiler:** Bölgeye girişte selam verir, parça ve kristalde sevinir, avcı uyanınca korkar,
  ışık sönünce cesaret verir; 3 başarısızlıktan sonra ipucu teklif eder. Karlı dağda atkı takar.
- **Bağ:** Bölüm bitirmek +1, ★★★ +1 daha, Pıtır'ı sevmek (bölümde bir kez) +1 puan.
  Seviyeler: Tanışma 0, Arkadaş 8, Yol arkadaşı 25, Can dostu 50, Aile 90 puan.
  Can dostu seviyesinde ışık yarıçapı +0,3 kare olur.
- **Görünüm:** Camgöbeği ışıktan, iri sivri kulaklı, iri parlak gözlü küçük bir ruh; başında yeşil bir filiz,
  göğsünde beyaz tüy, ucu parlayan kabarık bir kuyruk.
- **Gelişim (bağ seviyesine göre):**
  - *Başlangıç* (Tanışma, Arkadaş): kanatsız, hafif camgöbeği hale.
  - *Orta* (Yol arkadaşı): sarımsı kristal kanatlar, kulak içleri sarı, filiz parlar.
  - *İleri* (Can dostu): büyük pembe-mor kanatlar, mor hale, etrafında dönen 3 pırıltı.
  - *Ustalık* (Aile): gökkuşağı kanatlar, altın taç, altın hale, 5 pırıltı.
  - Seviye atlanınca bölüm sonunda ışık dalgasıyla yeni görünüme geçer.
- Bitirilen her bölge kuyruğuna bir renk boncuğu ekler.
- **Yetenekler** aşamalara göre açılır. Düğmeli olanlar alt sağda durur; her biri denemede bir kez kullanılır.

  | Aşama | Yetenek | Kullanım | Ne yapar |
  |---|---|---|---|
  | Başlangıç | Renk Görüşü | Otomatik + GÖSTER düğmesi | Lumi saklı bir şeye 2 kare yaklaşınca Pıtır fark eder. GÖSTER (1 hamle) `?` parçaları ve `:` köprüleri ortaya çıkarır. Saklı parça: 5, 9, 13, 19, 34, 38, 47; köprü: 16, 44 |
  | Başlangıç | Renk Dokunuşu | Otomatik | Lumi `P` solmuş çiçeğin yanına gelince Pıtır renk verir, çiçek açar, `G` kök kapılar çekilir (Pembe Kiraz) |
  | Başlangıç | Fener | FENER düğmesi | Mağarada 4 hamle boyunca ışık +1 kare (1 hamle) |
  | Orta | Kristal Sezgisi | Otomatik | Saklı parçaların ve görünmez köprülerin yeri kesik çizgiyle hep görünür |
  | Orta | Işık Köprüsü | KÖPRÜ düğmesi | Su/boşluk olan bölümlerde Lumi'nin baktığı yöne 2 karelik geçici köprü, 8 hamle sürer (2 hamle) |
  | İleri | Işık Kalkanı | Otomatik | Denemede bir kez diken ya da gölge ışığı söndüremez; dikende Lumi geri seker |
  | İleri | Ruhsal Geçiş | GEÇİŞ düğmesi | Pıtır duvarlardan geçer: 4 kare içindeki kristali ya da solmuş çiçeği çalıştırır, yoksa 3 kare içindeki parçayı getirir (2 hamle) |
  | Ustalık | Uyanış | Otomatik | Fener 6 hamle ve +1,5 kare, köprü 3 kare, 2 kalkan, 3 ipucu |

  Bölümlerin gerektirdiği yetenekler (Renk Görüşü, Renk Dokunuşu) Başlangıç'tan beri açıktır; sonraki aşamaların
  yetenekleri isteğe bağlı yardımlardır. Köprü ve Geçiş yolu kısaltabildiği için 2 hamle sayılır.
- Par değerleri `python -I tools/solve.py` ile denetlenir; bu çözücü yeni kareleri ve "Göster" hamlesini de bilir.

### 3.5 Kurallar (v1)
- Her bölümde tam 3 ışık parçası vardır.
- Engele (diken veya gölge) çarpmak bölümü yeniden başlatır. Ceza yoktur, sadece tekrar denenir.
- Bölümü tamamlamak sonraki bölümü açar ve **adım sayısına göre 1–3 yıldız** verir:
  - Her bölümün bir **hedef adım sayısı** vardır: çözücünün bulduğu en kısa çözüm.
    Buz kayması ve rüzgâr zinciri tek adım sayılır.
  - ★★★ "Mükemmel!": hedef + %15 (en az +2) adıma kadar
  - ★★ "Çok iyi!": hedef + %50 (en az +6) adıma kadar
  - ★ "Tamamlandı": daha fazla adımla
  - Oyun sırasında üst çubukta adım sayısı ve o an kazanılacak yıldızlar görünür.
  - Tamamlandı kartında yıldızlar sırayla belirir; 3'ten azsa "3 yıldız için en fazla N adım" yazar.
  - En iyi sonuç saklanır; daha iyi bitirilirse "Yeni rekor!" yazar. Toplam: 50 × 3 = 150 yıldız.
- İlerleme cihazda saklanır (SharedPreferences).

## 4. Bölümler

**50 bölüm**, onar bölümlük **5 bölgeye** ayrılır. Her bölüm kendi rengini geri getirir.
Bir bölgenin son bölümü bitince sonraki bölge açılır. Her bölge yeni bir mekanik getirir ve
zorluk bölge içinde de bölgeler arasında da artar. Orman'ın ilk 5 renginden her biri ana menüdeki
manzaranın bir parçasını boyar.

| Bölge | Bölümler | Yeni mekanik | Ortam |
|---|---|---|---|
| 1. Orman | 1–10 | Toplama, dikenler, gölgeler, kristal + kapı | Gece ormanı, çam siluetleri |
| 2. Göl | 11–20 | Tek kullanımlık nilüferler, sis | İskele, su, kamış, ay yansıması |
| 3. Mağara | 21–30 | Avcı gölgeler, çok dar ışık | Sarkıt, dikit, kristal öbekleri |
| 4. Karlı Dağ | 31–40 | **Buz:** engele çarpana kadar kayılır | Karlı zirveler, kuzey ışığı, yağan kar |
| 5. Bulut Bahçesi | 41–50 | **Rüzgâr:** ok yönüne itilir; bulutlar arası boşluklar | Gün batımı göğü, uçan adalar |

**Bölüm adları**

- **Orman:** Kırmızı Çiçekler, Turuncu Yapraklar, Sarı Güneş, Yeşil Çimenler, Mavi Gökyüzü,
  Mor Menekşeler, Pembe Kiraz, Altın Başaklar, Gümüş Çiy, Orman Kalbi
- **Göl:** Pembe Nilüferler, Yeşil Kamışlar, Altın Ateş Böcekleri, Turkuaz Sis, Mor Ay Işığı,
  Gümüş Balıklar, Su Perisi, Kamış Labirenti, Yakamoz, Ay Gölü
- **Mağara:** Camgöbeği Kristaller, Mercan Tüneller, Eflatun Mantarlar, Nane Labirenti, Gökkuşağı Kalbi,
  Ametist Mağarası, Lav Damarı, Yankılı Kuyu, Zümrüt Damla, Derin Kalp
- **Karlı Dağ:** İlk Kar, Buz Pateni, Kristal Saçak, Kar Tanesi, Kuzey Işığı, Donmuş Şelale, Tipi,
  Buz Sarayı, Zirve Yolu, Kar Kraliçesi
- **Bulut Bahçesi:** Pamuk Bulutlar, Rüzgâr Gülü, Gün Batımı, Uçan Adalar, Gökkuşağı Köprüsü,
  Yıldız Tozu, Fırtına Gözü, Şimşek Bahçesi, Şafak, Işığın Evi (final)

**Zorluk eğrisi**
- Gölge hızı 0,5'ten 0,28 saniye/kareye iner.
- Avcı hızı 0,6'dan 0,42 saniye/kareye çıkar.
- Taban ışık ormanda 2,4, mağaranın sonunda 1,5'tir. Karlı dağda 2,4'ten 1,9'a, bulutlarda 2,6'dan 2,0'a iner.
- Her bölgenin ilk bölümü yeni mekaniği yalnız başına tanıtır, sonraki bölümler gölge, avcı, diken
  ve kapıyı ekler.

**Bölüm üretimi ve doğrulama**
- İlk 15 bölüm elle tasarlandı. Kalan 35 bölüm, çözücüsü içinde olan bir **bölüm üreticisiyle**
  oluşturuldu: her bölüm için binlerce aday harita üretilir ve kurallara uymayanlar elenir.
- Çözücü tam durum uzayında BFS yapar. Durum şunlardan oluşur: konum, toplanan parçalar,
  kristal ve batan nilüferler. Buz kaymaları ve rüzgâr zincirleri tek hamle sayılır.
- Bir aday şu koşullarla kabul edilir:
  - çözülebilir olmalı ve hamle sayısı hedef aralıkta olmalı
  - göl bölümlerinde nilüferler gerçekten gerekli olmalı; ileri bölümlerde tek kullanımlık olmaları
    çözümü uzatmalı
  - buz ve rüzgâr bölümlerinde çözüm en az belirli sayıda kayma ya da itme içermeli; mekanik
    kaldırılınca bölüm aynı kolaylıkta kalmamalı
  - rüzgâr zincirlerinde sonsuz döngü olmamalı
  - gölge şeritleri parça veya kristal içermemeli, uçları çıkmaz sokak olmamalı ve başlangıca
    2 kareden yakın olmamalı
  - avcılar başlangıca 6, parçalara ve çıkışa 3 kareden yakın olmamalı
- Kabul edilenler arasından en karmaşık olanı seçilir. Ölçüt: keşfedilen durum sayısı, kayma ve itme
  sayısı, nilüfer farkı ve çözüm uzunluğu.
- 50 bölümün hepsi son hâliyle yeniden çözülüp doğrulanmıştır.

Haritalar `app/src/main/java/com/lumi/kayiprenkler/Levels.kt` dosyasında metin olarak durur.
Yeni bölüm eklemek için bir metin bloğu yazmak yeterlidir. Örnek (Bölüm 4):

```
L...#.*
.##.#..
.^...#.
.#C#...
...#.#^
##D####      ← kristal açana kadar alt yarı kapalı
*.....^
.#^##..
...#*..
.#..#E.
```

## 5. Ekranlar

1. **Ana menü:** "Lumi" logosu, süzülen Lumi, renk ilerlemesi (5 renk noktası),
   *Oyna* (kaldığın bölüm), *Bölümler*, *Ses: Açık/Kapalı*. Arka plandaki orman,
   kazanılan renklere göre boyanır.
2. **Oyun alanı:** Üst çubukta geri, bölüm adı ve yeniden başlat düğmeleri.
   Altında 3 küre olarak enerji sayacı, ortada harita, en altta bölüm ipucu.
3. **Bölüm tamamlandı:** Zıplayarak beliren yıldız, "… geri döndü!" mesajı, süre,
   *Sonraki Bölüm* (son bölümde *Ana Menü*) ve *Tekrar Oyna*.
4. **Bölüm seçimi (harita):** Kaydırılabilir harita, 5 bölge. Her bölgede manzaralı bir altıgen kart,
   bölge adı, ve iki sıra hâlinde yılan gibi dizilmiş, kesik çizgiyle bağlı 10 altıgen bölüm düğümü
   (kilitli / açık / renkli ve yıldızlı) bulunur. Harita açılınca sıradaki bölümün bölgesine kayar.
   Altında "Sıradaki", "Bölge tamamlandı" ya da "Açmak için N. bölümü bitir" yazar.

5. **Lumi sayfası:** Ana menüde sağ üstteki düğmeyle ya da Lumi'ye dokunarak açılır. Tam boy Lumi ve yanında Pıtır,
   kişilik (Cesur, Meraklı, İyimser, Kararlı), renk paleti, dört detay kartı (saç aksesuarı: renk kristali, kolye:
   enerji kaynağı, çanta: keşif eşyaları, pelerin detayı: ışık sembolü) ve Pıtır'la bağ: seviye, kalpler, sonraki
   seviyeye kalan puan, açık yetenekler ve sonraki aşamada açılacaklar. Lumi'ye ya da Pıtır'a dokununca yakınlık anı oynar.

Android geri tuşu: Oyun ve Bölümler ekranından ana menüye döner, ana menüden çıkar.

## 6. Görsel yön

**Arayüz (v2):** Karakter sayfalarındaki gece mavisi, camgöbeği ışıltılı tarz. Gece mavisi zemin, yarı saydam cam
paneller (camgöbeği kenar), ışıklı hap düğmeler (ana eylem camgöbeği-mavi dolgulu), serif başlıklar.
- *Ana menü:* üstte karakter sayfasının sahnesi (logo, söz, Lumi ve Pıtır), altında Devam Et / Oyuna Başla,
  Bölüm Seç, Karakter ve Yoldaş, Ses; en altta "Lumi · Kayıp Renkler" alt yazısı.
- *Bölüm Seç:* başlık ve yıldız çipi; her bölge cam panel, köşede numaralı bölge sahnesi; açık bölümler koyu mavi
  altıgen, biten bölümler bölüm renginde.
- *Oyun içi üst panel:* geri, Lumi'nin portresi (duruma göre normal, mutlu, üzgün, şaşkın, kararlı ifade), bölüm
  numarası ve adı, ışık çubuğu, adım ve yıldızlar, yeniden başlat.
- *Karakter / Yoldaş:* alt sekmeli sayfa. Yoldaş sekmesinde Pıtır'ın tanıtım görseli, bağ seviyesi ve her yeteneğin
  açık ya da kilitli durumu.
- *Bölüm Tamamlandı:* yıldızlar, bölge sahnesi, yapılanlar listesi, ödüller (yıldız, bağ puanı, yeni bölge),
  Devam Et, Harita, Tekrar.
- Görseller `res/drawable-nodpi`: `ui_hero`, `ui_pitir_hero`, `face_*`, `scene_0..3`.

Görsel referanslar: masalsı gece ormanı (lavanta ağaçlar, ateş böcekleri, parlayan altın fidan),
altıgen bölüm haritası, kurdeleli "tamamlandı" kartı, ızgarada parlayan ışık izi
ve kanatlı, parlayan sevimli bir karakter. Bu referanslar ilham kaynağıdır; oyundaki bütün
görseller koddan özgün olarak çizilir.

- **Gece ve lavanta:** Renksiz orman mor-lavanta ve lacivert tonlardadır. Ağaç taçları
  çizgi film tarzında, kalın kenar çizgili ve üstü küçük çiçeklidir.
- **Ana menü manzarası:** Gece gökyüzü, hilal ay, katmanlı orman siluetleri, iki büyük ağaç,
  eğreltiotları, mantarlar, ortada tepecikte parlayan altın **renk fidanı**, uçuşan ateş böcekleri,
  uçurum ve göl. Kazanılan her renk sahnede bir öğeyi boyar:
  kırmızı → sol ağaç, turuncu → sağ ağaç, sarı → ay, mantarlar ve daha çok ateş böceği,
  yeşil → çimen ve eğreltiotları, mavi → gökyüzü ve göl.
- **Oyun alanı:** Aralıksız kare ızgara; üstte ve altta saydam çam siluetleri.
  Lumi'nin geçtiği kareler altın sarısı **ışık izi** olarak kalır ve karanlıkta da parlar.
  Hareket ederken arkasında turuncu bir kuyruk bırakır.
- **Bölge ortamları (oyun ekranı):**
  - *Orman:* mor-turkuaz gece, üstte ve altta çam siluetleri, çimen zemin, ağaç duvarlar.
  - *Göl:* lacivert gökyüzü, hilal ay ve sudaki yansıması, uzak tepeler, kamışlar, ateş böcekleri.
    Zemin ahşap iskele tahtalarıdır. Su kareleri dalgalanır, nilüferler pembe çiçeklidir.
    Batan nilüfer küçülür ve halka halka dalga bırakır. Duvarlar sudaki kaya ve kamış öbekleridir.
    Sisli bölümlerde tahtanın üstünde sis bulutları süzülür.
  - *Mağara:* tavandan sarkıtlar ve damlayan su, yerde dikitler ve parlayan kristal öbekleri,
    havada toz parçacıkları. Zemin çatlaklı taştır; duvarlar köşeli kayalardır, bazılarından kristal çıkar.
    Avcı gölgeler kırmızı ve boynuzludur: uyurken gözleri kapalıdır ve "z" çıkarır, uyanınca gözleri turuncu yanar.
  - *Karlı Dağ:* lacivert gece, dalgalanan kuzey ışığı perdeleri, karlı zirveler, altta karlı çamlar,
    ekranın her yerinde yağan kar. Zemin parıltılı kardır; buz kareleri mavi ve çapraz parıltılıdır;
    duvarlar üstü karlı çamlardır.
  - *Bulut Bahçesi:* mor-pembe gökyüzü, yavaşça kayan bulut kümeleri, süzülen küçük uçan adalar.
    Zemin kabarık bulut platformlarıdır. Boşluklarda aşağıdaki gökyüzü ve yıldızlar görünür.
    Rüzgâr karelerinde itme yönünde akan oklar vardır; duvarlar kalın bulut sütunlarıdır.
- **Renk dalgası:** Bölüm bitince dalga fidandan dışa doğru yayılır. Zemin bölümün yaprak
  rengine, ışık izi bölümün rengine (ör. kırmızı) döner, karelerde çiçekler açar, ağaçlar renklenir,
  gökyüzü bölümün renklerine kavuşur.
- **Lumi:** Dalgalı kahverengi saçlı genç bir kaşif. Saçında ucunda renk kristali olan altın yaprak tokası,
  beyaz ve altın işlemeli mavi astarlı bir pelerin, koyu bir yelek, deri çanta ve botlar. Boynundaki mavi enerji
  kristali oyundaki ışığın kaynağıdır. Yürürken adım atar, sola dönünce aynalanır, yukarı giderken sırtı görünür.
  Kişiliği: cesur, meraklı, iyimser, kararlı.
- **Oyun içi animasyonlar** (oyunda Lumi'nin küçük/chibi pozları kullanılır, sola giderken aynalanır):
  - *Durma:* nefes alır gibi hafifçe büyüyüp küçülür. *Koşma:* koşu ve duruş pozu sırayla, adım başına küçük sıçrama.
  - *Zıplama:* nilüfere basınca, rüzgâr itince ve bölüm bitince; kalkışta ve inişte ezilir, havada zıplama pozu.
    Bölüm bitince sevinçle zıplamaya devam eder.
  - *Etkileşim:* parça toplayınca, kristale dokununca, çiçek açınca ve Pıtır'ı severken öne uzanır, elinden pırıltı çıkar.
  - *Yakınlık:* Pıtır'a dokununca ve oyuncu 7 saniye hamle yapmayınca Lumi Pıtır'a döner, Pıtır omzuna konar,
    ikisinin etrafında pembe-camgöbeği bir ışık belirir ve kalpler yükselir (1,8 saniye).
  - *Özel yetenek:* bir yetenek kullanılınca hafifçe yükselir, ayaklarının çevresinde altın ve camgöbeği halkalar döner,
    ışık parçacıkları yükselir. Pıtır aynı anda bir tur döner ve ışık saçar.
- **Gölgeler:** Dalgalı etekli koyu mor hayaletler. Gözleri karanlıkta parlar.
- **Arayüz:** Krem kenarlı altın ve kırmızı hap düğmeler, yuvarlak simge düğmeleri, oyunda saydam
  halka düğmeler ve altın ışık çubuğu. Bölüm haritasında altıgen düğümler kesik çizgiyle bağlanır:
  kilitli olanlar koyu, açık olanlar beyaz, bitenler renkli ve yıldızlıdır. Bir sonraki bölümler için
  "Deniz" bölgesi kilitli olarak görünür.
- **Uygulama simgesi:** Orman arka planı önünde omuz çekiminde Lumi (saç tokası, mavi yaka, boynunda kristal) ve yanında
  camgöbeği Pıtır. Bütün içerik ortadaki 66dp güvenli dairede kalır.
- Lumi ve Pıtır, karakter tasarım sayfalarından kesilmiş saydam PNG görsellerle çizilir (`res/drawable-nodpi`:
  `lumi_front`, `lumi_side`, `lumi_back`, `pitir_0..3`); uygulama simgesinin ön planı da bunlardan yapılmıştır
  (`ic_lumi_fg`). Dünya, nesneler ve arayüz kodla çizilir (Canvas). Görseller yüklenemezse karakterler de koddan çizilir.

## 7. Ses

Ses efektleri kodla sentezlenir (AudioTrack), ses dosyası yoktur:
dokunma, adım, parça toplama, enerji doldu arpeji, kristal çınlaması,
ışık söndü ve bölüm bitti melodisi. Menüden kapatılabilir ve bu tercih saklanır.

## 8. Teknik mimari

```
MainActivity  → tam ekran, dikey, geri tuşu
GameView      → tek View; 60 fps döngü, ekranlar (MENU / SELECT / GAME + tamamlandı katmanı),
                dokunma/kaydırma, bütün çizimler
Game          → oyun mantığı (harita, BFS yol bulma, gölge devriyesi, çarpışma, olaylar)
Levels        → 5 bölge ve 50 bölümün metin haritaları, renk paletleri, ışık ve hız ayarları
Progress      → açık bölümler, yıldızlar, ses tercihi
Sfx           → sentezlenmiş ses efektleri
```

Harici kütüphane yoktur, yalnızca Android SDK ve Kotlin kullanılır.

## 9. Sonraki sürümler (v2+ fikirleri)

- **Aynalar:** Işık ışınını döndürülebilir aynalarla kristale ve çiçeğe yönlendirme.
- **Sıralı toplama:** Parçaları renk sırasıyla (kırmızı → sarı → mavi) toplama.
- **Renk karıştırma:** Kırmızı ve mavi ışıkla mor kapıyı açma.
- 3 yıldız sistemi (süre ve adım sayısına göre), yeni bölgeler (göl, mağara), müzik.
