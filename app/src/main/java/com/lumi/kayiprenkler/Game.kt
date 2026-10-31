package com.lumi.kayiprenkler

import kotlin.math.abs
import kotlin.math.hypot

enum class Tile { FLOOR, TREE, THORN, DOOR, CRYSTAL, EXIT, WATER, LILY, ICE, WIND, BRIDGE, BLOOM, GATE }

enum class GameState { PLAYING, DYING, WON }

enum class GameEvent { STEP, SHARD, ENERGY_FULL, CRYSTAL, FAIL, WIN, SPLASH, HUNTER, WIND, REVEAL, LANTERN, BLOOM, SHIELD, LIGHT_BRIDGE, SPIRIT, HOP }

/** Pıtır'ın bölümde bir kez kullanılan yeteneği. */
enum class Ability { REVEAL, LANTERN }

/** Kareden kareye kayan bir nesne; px/py çizim için ara konumu verir. */
class Mover(var x: Int, var y: Int) {
    var fromX = x
    var fromY = y
    var t = 1f

    val px: Float get() = fromX + (x - fromX) * t
    val py: Float get() = fromY + (y - fromY) * t

    fun moveTo(nx: Int, ny: Int) {
        fromX = x; fromY = y; x = nx; y = ny; t = 0f
    }

    fun place(nx: Int, ny: Int) {
        x = nx; y = ny; fromX = nx; fromY = ny; t = 1f
    }
}

/**
 * Canavar türleri. Her birinin tek, kolay anlaşılır bir farkı var: [speed] adım süresi çarpanı (büyük = yavaş),
 * [pause] dönüşte bekleme (saniye), avcılar için [wake] uyanma ve [lose] bırakma mesafesi (adım).
 * [trait] ne yaptığını, [tip] nasıl geçileceğini anlatır (ilk karşılaşmada tanıtım kartında görünür).
 */
enum class Monster(
    val title: String, val trait: String, val tip: String,
    val speed: Float = 1f, val pause: Float = 0f, val wake: Int = 4, val lose: Int = 7,
) {
    GOLGE("Gölge", "Şeridinde ileri geri devriye gezer.", "Geçmeden önce ritmini izle."),
    CICEK("Çiçek Canavarı", "Çok yavaş devriye gezer.", "Acele etme, arkasından geç.", speed = 1.6f),
    SU("Su Ruhu", "Hızlı süzülür.", "Geçtiği yerden hemen sonra geç.", speed = 0.8f),
    YENGEC("Kaya Yengeci", "Yan yan yürür, her dönüşte biraz bekler.", "Beklediği anda yanından geç.", pause = 0.6f),
    GOLEM("Taş Golemi", "Uyur; uzaktan uyanır ama çok yavaş kovalar.", "Uyanırsa koşarak uzaklaş.", speed = 1.4f, wake = 5),
    ATES("Ateş Böceği", "Hızlı uçar, karanlıkta parlar.", "Parıltısından yerini izle.", speed = 0.75f),
    KUS("Karanlık Kuş", "Çok hızlı uçar.", "Yolunun sonuna varmasını bekle.", speed = 0.7f),
    SOLUCAN("Renk Solucanı", "Uyur; uyanınca uzun süre peşini bırakmaz.", "Uyandırmamaya çalış.", lose = 9),
}

/** Canavar: devriye gezer ya da [hunter] ise yerinde uyur, Lumi yaklaşınca uyanıp peşine düşer. [wait] dönüşteki bekleme. */
class Shadow(x: Int, y: Int, var dx: Int, var dy: Int, val hunter: Boolean = false, val kind: Monster = Monster.GOLGE) {
    val m = Mover(x, y)
    var awake = false
    var wait = 0f

    /** Uyandığı anda 1'den 0'a iner (başında "!" belirir). */
    var alert = 0f
}

/**
 * [bonusLight]: Pıtır'la bağ yeterince güçlüyse kuyruğunun yaydığı ek ışık (kare).
 * [stage]: Pıtır'ın gelişim aşaması (-1 yok, 0 Başlangıç, 1 Orta, 2 İleri, 3 Ustalık); hangi yeteneklerin
 * açık olduğunu ve ne kadar güçlü olduklarını belirler.
 */
class Game(
    val index: Int,
    private val bonusLight: Float = 0f,
    val stage: Int = -1,
    private val onEvent: (GameEvent) -> Unit,
) {

    val def = Levels.all[index]
    val cols = Levels.COLS
    val rows = Levels.ROWS
    val tiles = Array(rows) { Array(cols) { Tile.FLOOR } }

    private val shardStarts = ArrayList<Pair<Int, Int>>()

    /** Pıtır gösterene kadar görünmeyen ve toplanamayan ışık parçaları (`?`). */
    private val hiddenStarts = ArrayList<Pair<Int, Int>>()
    private val blooms = ArrayList<Pair<Int, Int>>()
    private val shadowStarts = ArrayList<IntArray>()
    private var startX = 0
    private var startY = 0

    var exitX = 0; private set
    var exitY = 0; private set

    /** Lumi'nin geçtiği kareler ışıkla işaretlenir (ışık izi). */
    private val lit = BooleanArray(cols * rows)

    /** Batan nilüferler: -1 batmadı, aksi hâlde batalı geçen süre (animasyon için). */
    private val sunkAge = FloatArray(cols * rows) { -1f }

    val shards = ArrayList<Pair<Int, Int>>()

    /** Bu bölümdeki canavar türleri (Pıtır ilk karşılaşmada tanıtır). */
    val monsterKinds: Set<Monster> get() = shadows.map { it.kind }.toSet()

    /** Bölgeye göre canavar: orman gölge ve çiçek, göl su ruhu, mağara yengeç ve golem, dağ ateş böceği ve golem, bulut kuş ve solucan. */
    private fun monsterFor(ch: Char): Monster = when (Levels.themeOf(index)) {
        Theme.FOREST -> if (ch == 'V' && index >= 5) Monster.CICEK else Monster.GOLGE
        Theme.LAKE -> Monster.SU
        Theme.CAVE -> if (ch == 'A') Monster.GOLEM else Monster.YENGEC
        Theme.SNOW -> if (ch == 'A') Monster.GOLEM else Monster.ATES
        Theme.CLOUD -> if (ch == 'A') Monster.SOLUCAN else Monster.KUS
    }
    val shadows = ArrayList<Shadow>()
    val lumi = Mover(0, 0)

    /** Yoldaş Pıtır: Lumi'yi bir kare geriden izler (Lumi'nin bir önceki karesinde durur). */
    val pitir = Mover(0, 0)

    /** Oyuncunun son hamlesinden beri geçen süre; uzun durunca Pıtır ipucu verir. */
    var idle = 0f; private set
    var dirX = 0; private set
    var dirY = 1; private set

    var collected = 0; private set

    /** Oyuncunun bu denemede yaptığı hamle sayısı (kayma ve rüzgâr zincirleri sayılmaz). */
    var moves = 0; private set

    /** Şu anki hamle sayısıyla bölüm biterse kazanılacak yıldız. */
    val starsNow: Int get() = def.starsFor(moves)
    var doorsOpen = false; private set

    /** Pıtır'ın yeteneği: gizlileri göstermek (`?`, `:` olan bölümler) ya da mağarada kuyruk feneri. */
    val ability: Ability? = when {
        def.map.any { r -> r.any { it == '?' || it == ':' } } -> Ability.REVEAL
        Levels.themeOf(index) == Theme.CAVE -> Ability.LANTERN
        else -> null
    }
    var abilityUsed = false; private set
    var revealed = false; private set

    /** Kuyruk fenerinin kalan hamlesi; yanarken ışık 1 kare büyür. */
    var lanternMoves = 0; private set
    var bloomed = false; private set

    /** Işık Kalkanı (İleri): kalan koruma sayısı ve korumadan sonraki dokunulmazlık süresi. */
    var shields = 0; private set
    var shieldTime = 0f; private set

    /** Işık Köprüsü (Orta): su ya da boşluk üstünde geçici ışık kareleri ve kalan hamle. */
    private val lightBridge = BooleanArray(cols * rows)
    var bridgeMoves = 0; private set
    var bridgeUsed = false; private set

    /** Ruhsal Geçiş (İleri): Pıtır duvarların içinden geçip bir mekanizmayı çalıştırır ya da parça getirir. */
    var spiritUsed = false; private set
    var spiritTarget: Pair<Int, Int>? = null; private set

    /** Orta aşamadan itibaren açık: su/boşluk olan bölümlerde. */
    val canBridge: Boolean = stage >= 1 && def.map.any { r -> r.any { it == '~' || it == 'o' } }
    val canSpirit: Boolean = stage >= 2
    var state = GameState.PLAYING; private set
    var stateTime = 0f; private set
    var elapsed = 0f; private set

    val totalShards: Int get() = shardStarts.size + hiddenStarts.size
    val energyFull: Boolean get() = collected >= totalShards

    /** Oyuncunun sıradaki yön komutları (dx, dy). */
    private val queue = ArrayDeque<Pair<Int, Int>>()

    /** Rüzgâr karelerinin itme yönü. */
    private val windX = IntArray(cols * rows)
    private val windY = IntArray(cols * rows)

    /** Buz veya rüzgâr yüzünden kendiliğinden devam eden hareket; null ise Lumi serbest. */
    private var forced: Pair<Int, Int>? = null
    private var forcedSteps = 0

    /** O anki adımın süresi: kayarken ve rüzgârda daha hızlı. */
    private var stepTime = LUMI_STEP

    fun windDir(x: Int, y: Int): Pair<Int, Int> = windX[y * cols + x] to windY[y * cols + x]

    init {
        for ((y, row) in def.map.withIndex()) {
            for ((x, ch) in row.withIndex()) {
                tiles[y][x] = when (ch) {
                    '#' -> Tile.TREE
                    '^' -> Tile.THORN
                    'D' -> Tile.DOOR
                    'C' -> Tile.CRYSTAL
                    'E' -> Tile.EXIT
                    '~' -> Tile.WATER
                    'o' -> Tile.LILY
                    '=' -> Tile.ICE
                    '>', '<', 'n', 'u' -> Tile.WIND
                    ':' -> Tile.BRIDGE
                    'P' -> Tile.BLOOM
                    'G' -> Tile.GATE
                    else -> Tile.FLOOR
                }
                when (ch) {
                    '>' -> windX[y * cols + x] = 1
                    '<' -> windX[y * cols + x] = -1
                    'n' -> windY[y * cols + x] = -1
                    'u' -> windY[y * cols + x] = 1
                }
                when (ch) {
                    'L' -> { startX = x; startY = y }
                    'E' -> { exitX = x; exitY = y }
                    '*' -> shardStarts.add(x to y)
                    '?' -> hiddenStarts.add(x to y)
                    'P' -> blooms.add(x to y)
                    'H' -> shadowStarts.add(intArrayOf(x, y, if (x > cols / 2) -1 else 1, 0, 0, monsterFor('H').ordinal))
                    'V' -> shadowStarts.add(intArrayOf(x, y, 0, if (y > rows / 2) -1 else 1, 0, monsterFor('V').ordinal))
                    'A' -> shadowStarts.add(intArrayOf(x, y, 0, 0, 1, monsterFor('A').ordinal))
                }
            }
        }
        restart()
    }

    fun restart() {
        shards.clear()
        shards.addAll(shardStarts)
        shadows.clear()
        for (s in shadowStarts) shadows.add(Shadow(s[0], s[1], s[2], s[3], hunter = s[4] == 1, kind = Monster.entries[s[5]]))
        lumi.place(startX, startY)
        pitir.place(startX, startY)
        idle = 0f
        lit.fill(false)
        sunkAge.fill(-1f)
        lit[startY * cols + startX] = true
        dirX = 0; dirY = 1
        queue.clear()
        forced = null
        forcedSteps = 0
        collected = 0
        moves = 0
        doorsOpen = false
        abilityUsed = false
        revealed = false
        lanternMoves = 0
        bloomed = false
        shields = when {
            stage >= 3 -> 2
            stage >= 2 -> 1
            else -> 0
        }
        shieldTime = 0f
        lightBridge.fill(false)
        bridgeMoves = 0
        bridgeUsed = false
        spiritUsed = false
        spiritTarget = null
        state = GameState.PLAYING
        stateTime = 0f
        elapsed = 0f
    }

    fun lightRadius(): Float =
        def.light + 0.55f * collected + bonusLight + if (lanternMoves > 0) (if (stage >= 3) 1.5f else 1f) else 0f

    fun isLightBridge(x: Int, y: Int) = lightBridge[y * cols + x]

    fun isLit(x: Int, y: Int) = lit[y * cols + x]

    fun isSunk(x: Int, y: Int) = sunkAge[y * cols + x] >= 0f

    /** Henüz gösterilmemiş gizli parça bu karede mi. */
    fun hiddenAt(x: Int, y: Int) = !revealed && (x to y) in hiddenStarts

    /** Renk Görüşü: [r] kare yakında saklı parça, görünmez köprü ya da solmuş çiçek var mı. */
    fun secretNear(x: Int, y: Int, r: Int): Boolean {
        for (yy in (y - r)..(y + r)) for (xx in (x - r)..(x + r)) {
            if (abs(xx - x) + abs(yy - y) > r) continue
            val t = tileAt(xx, yy) ?: continue
            if (hiddenAt(xx, yy) || (t == Tile.BRIDGE && !revealed) || (t == Tile.BLOOM && !bloomed)) return true
        }
        return false
    }

    /**
     * Pıtır'ın yeteneğini kullanır; bir hamle sayılır. Gizliyi göstermek bütün `?` parçalarını ve `:` köprülerini
     * ortaya çıkarır, kuyruk feneri [LANTERN_MOVES] hamle boyunca ışığı büyütür.
     */
    fun useAbility(): Boolean {
        val a = ability ?: return false
        if (abilityUsed || state != GameState.PLAYING) return false
        abilityUsed = true
        moves++
        idle = 0f
        when (a) {
            Ability.REVEAL -> {
                revealed = true
                shards.addAll(hiddenStarts)
                onEvent(GameEvent.REVEAL)
            }
            Ability.LANTERN -> {
                lanternMoves = if (stage >= 3) LANTERN_MOVES + 2 else LANTERN_MOVES
                onEvent(GameEvent.LANTERN)
            }
        }
        return true
    }

    fun sinkAge(x: Int, y: Int) = sunkAge[y * cols + x]

    /**
     * Işık Köprüsü: Lumi'nin baktığı yöndeki su/boşluk karelerine 2 (Ustalıkta 3) karelik geçici bir köprü kurar.
     * [BRIDGE_MOVES] hamle sürer; iki hamle sayılır. Önünde su yoksa kurulamaz.
     */
    fun useBridge(): Boolean {
        if (!canBridge || bridgeUsed || state != GameState.PLAYING || lumi.t < 1f) return false
        val dx = dirX
        val dy = if (dirX == 0 && dirY == 0) 1 else dirY
        val len = if (stage >= 3) 3 else 2
        var x = lumi.x + dx
        var y = lumi.y + dy
        var made = 0
        while (made < len && tileAt(x, y).let { it == Tile.WATER || (it == Tile.LILY && isSunk(x, y)) || (it == Tile.BRIDGE && !revealed) }) {
            lightBridge[y * cols + x] = true
            made++
            x += dx
            y += dy
        }
        if (made == 0) return false
        bridgeUsed = true
        bridgeMoves = BRIDGE_MOVES
        moves += 2
        idle = 0f
        onEvent(GameEvent.LIGHT_BRIDGE)
        return true
    }

    /**
     * Ruhsal Geçiş: Pıtır duvarların içinden geçer. Sırayla dener: 4 kare içindeki kristal (kapıları açar),
     * solmuş çiçek (kökleri çeker), 3 kare içindeki en yakın ışık parçası (getirir). İki hamle sayılır.
     */
    fun useSpirit(): Boolean {
        if (!canSpirit || spiritUsed || state != GameState.PLAYING || lumi.t < 1f) return false
        fun dist(p: Pair<Int, Int>) = abs(p.first - lumi.x) + abs(p.second - lumi.y)
        var target: Pair<Int, Int>? = null
        if (!doorsOpen) {
            for (y in 0 until rows) for (x in 0 until cols) {
                if (tiles[y][x] == Tile.CRYSTAL && dist(x to y) <= 4) target = x to y
            }
            if (target != null) {
                doorsOpen = true
                onEvent(GameEvent.CRYSTAL)
            }
        }
        if (target == null && !bloomed) {
            target = blooms.firstOrNull { dist(it) <= 4 }
            if (target != null) {
                bloomed = true
                onEvent(GameEvent.BLOOM)
            }
        }
        if (target == null) {
            target = shards.filter { dist(it) <= 3 }.minByOrNull { dist(it) }
            if (target != null) {
                shards.remove(target)
                collected++
                onEvent(if (energyFull) GameEvent.ENERGY_FULL else GameEvent.SHARD)
            }
        }
        if (target == null) return false
        spiritUsed = true
        spiritTarget = target
        moves += 2
        idle = 0f
        onEvent(GameEvent.SPIRIT)
        return true
    }

    private fun tileAt(x: Int, y: Int): Tile? =
        if (x in 0 until cols && y in 0 until rows) tiles[y][x] else null

    private fun lumiCanEnter(x: Int, y: Int, avoidThorns: Boolean): Boolean =
        when (tileAt(x, y)) {
            null, Tile.TREE -> false
            Tile.WATER -> isLightBridge(x, y)
            Tile.LILY -> !isSunk(x, y) || isLightBridge(x, y)
            Tile.DOOR -> doorsOpen
            Tile.BRIDGE -> revealed || isLightBridge(x, y)
            Tile.BLOOM -> false
            Tile.GATE -> bloomed
            Tile.THORN -> !avoidThorns
            else -> true
        }

    private fun shadowCanEnter(x: Int, y: Int): Boolean {
        val t = tileAt(x, y)
        return t == Tile.FLOOR || (t == Tile.DOOR && doorsOpen) || (t == Tile.GATE && bloomed)
    }

    /** Kaydırma: tek yön komutu. Dikenlerden kaçınmaz. */
    fun swipe(dx: Int, dy: Int) {
        if (state != GameState.PLAYING) return
        queue.clear()
        queue.add(dx to dy)
    }

    /** Dokunma: hedefe giden en kısa güvenli komut dizisi (buz kaymaları ve rüzgâr dahil). */
    fun tapCell(tx: Int, ty: Int): Boolean {
        if (state != GameState.PLAYING || forced != null) return false
        if (tx == lumi.x && ty == lumi.y) return false
        val path = findPath(lumi.x, lumi.y, tx, ty) ?: return false
        queue.clear()
        queue.addAll(path)
        return true
    }

    /** Bir yön komutunun Lumi'yi nereye bırakacağı; geçilemezse ya da dikene/döngüye düşerse null. */
    private fun landing(sx: Int, sy: Int, dx0: Int, dy0: Int): Int? {
        var x = sx
        var y = sy
        var dx = dx0
        var dy = dy0
        var moved = false
        for (i in 0..MAX_FORCED) {
            val nx = x + dx
            val ny = y + dy
            if (!lumiCanEnter(nx, ny, avoidThorns = true)) {
                if (tileAt(nx, ny) == Tile.THORN) return null
                return if (moved) y * cols + x else null
            }
            x = nx
            y = ny
            moved = true
            when (tiles[y][x]) {
                Tile.ICE -> Unit
                Tile.WIND -> { dx = windX[y * cols + x]; dy = windY[y * cols + x] }
                else -> return y * cols + x
            }
        }
        return null
    }

    private fun findPath(sx: Int, sy: Int, tx: Int, ty: Int): List<Pair<Int, Int>>? {
        val start = sy * cols + sx
        val target = ty * cols + tx
        val prev = IntArray(cols * rows) { -1 }
        val prevDir = IntArray(cols * rows)
        val seen = BooleanArray(cols * rows)
        val open = ArrayDeque<Int>()
        seen[start] = true
        open.add(start)
        while (open.isNotEmpty()) {
            val c = open.removeFirst()
            if (c == target) break
            for ((k, d) in DIRS.withIndex()) {
                val n = landing(c % cols, c / cols, d.first, d.second) ?: continue
                if (seen[n]) continue
                seen[n] = true
                prev[n] = c
                prevDir[n] = k
                open.add(n)
            }
        }
        if (!seen[target]) return null
        val path = ArrayList<Pair<Int, Int>>()
        var c = target
        while (c != start) {
            path.add(DIRS[prevDir[c]])
            c = prev[c]
        }
        path.reverse()
        return path
    }

    private fun stepTo(nx: Int, ny: Int, fast: Boolean) {
        dirX = nx - lumi.x
        dirY = ny - lumi.y
        val leaving = lumi.y * cols + lumi.x
        val fromLily = tiles[lumi.y][lumi.x] == Tile.LILY
        pitir.moveTo(lumi.x, lumi.y)
        lumi.moveTo(nx, ny)
        stepTime = if (fast) SLIDE_STEP else LUMI_STEP
        if (fromLily) {
            sunkAge[leaving] = 0f
            onEvent(GameEvent.SPLASH)
        } else if (!fast) {
            onEvent(GameEvent.STEP)
        }
    }

    fun update(dt: Float) {
        stateTime += dt
        if (state != GameState.PLAYING) return
        elapsed += dt
        idle += dt
        if (pitir.t < 1f) pitir.t = (pitir.t + dt / stepTime).coerceAtMost(1f)
        for (i in sunkAge.indices) if (sunkAge[i] >= 0f) sunkAge[i] += dt

        if (lumi.t < 1f) {
            lumi.t = (lumi.t + dt / stepTime).coerceAtMost(1f)
            if (lumi.t >= 1f) arrive()
        }
        if (state == GameState.PLAYING && lumi.t >= 1f) {
            val f = forced
            if (f != null) {
                forced = null
                stepTo(lumi.x + f.first, lumi.y + f.second, fast = true)
            } else if (queue.isNotEmpty()) {
                val (dx, dy) = queue.removeFirst()
                if (lumiCanEnter(lumi.x + dx, lumi.y + dy, avoidThorns = false)) {
                    forcedSteps = 0
                    moves++
                    idle = 0f
                    if (lanternMoves > 0) lanternMoves--
                    if (bridgeMoves > 0) bridgeMoves--
                    stepTo(lumi.x + dx, lumi.y + dy, fast = false)
                } else {
                    queue.clear()
                }
            }
        }

        for (s in shadows) {
            if (s.alert > 0f) s.alert -= dt
            if (s.wait > 0f) {
                s.wait -= dt
                continue
            }
            val step = (if (s.hunter) def.hunterStep else def.shadowStep) * s.kind.speed
            s.m.t = (s.m.t + dt / step).coerceAtMost(1f)
            if (s.m.t >= 1f) {
                if (s.hunter) stepHunter(s) else stepShadow(s)
            }
        }

        if (shieldTime > 0f) shieldTime -= dt
        // köprü süresi dolunca, Lumi üstünde değilse söner
        if (bridgeMoves == 0 && bridgeUsed && lumi.t >= 1f && !isLightBridge(lumi.x, lumi.y)) lightBridge.fill(false)

        if (state == GameState.PLAYING && shieldTime <= 0f) {
            for (s in shadows) {
                if (hypot(s.m.px - lumi.px, s.m.py - lumi.py) < 0.6f) {
                    die()
                    break
                }
            }
        }
    }

    /**
     * Pıtır'ın göstereceği yer: Lumi'nin ulaşabildiği en yakın ışık parçası; yoksa kapalı kapılar
     * için kristal, enerji doluysa fidan. Hiçbiri yoksa null.
     */
    fun hintTarget(): Pair<Int, Int>? {
        if (energyFull) return exitX to exitY
        var best: Pair<Int, Int>? = null
        var bestLen = Int.MAX_VALUE
        for (sh in shards) {
            val p = findPath(lumi.x, lumi.y, sh.first, sh.second) ?: continue
            if (p.size < bestLen) { bestLen = p.size; best = sh }
        }
        if (best != null) return best
        if (!bloomed && blooms.isNotEmpty()) return blooms[0]
        if (!revealed && hiddenStarts.isNotEmpty()) return hiddenStarts[0]
        if (!doorsOpen) {
            for (y in 0 until rows) for (x in 0 until cols) if (tiles[y][x] == Tile.CRYSTAL) return x to y
        }
        return shards.firstOrNull()
    }

    private fun stepShadow(s: Shadow) {
        if (!shadowCanEnter(s.m.x + s.dx, s.m.y + s.dy)) {
            s.dx = -s.dx
            s.dy = -s.dy
            // dönüşte bekleyen canavar (yengeç)
            if (s.kind.pause > 0f) {
                s.wait = s.kind.pause
                return
            }
            if (!shadowCanEnter(s.m.x + s.dx, s.m.y + s.dy)) return
        }
        s.m.moveTo(s.m.x + s.dx, s.m.y + s.dy)
    }

    /** Avcı: Lumi türünün uyanma mesafesine gelince uyanır, en kısa yoldan kovalar; bırakma mesafesinden uzaklaşırsa durur. */
    private fun stepHunter(s: Shadow) {
        val path = shadowPath(s.m.x, s.m.y, lumi.x, lumi.y)
        val d = path?.size ?: Int.MAX_VALUE
        if (!s.awake && d <= s.kind.wake) {
            s.awake = true
            s.alert = 1f
            onEvent(GameEvent.HUNTER)
        } else if (s.awake && d > s.kind.lose) {
            s.awake = false
        }
        if (s.awake && path != null && path.isNotEmpty()) {
            val (nx, ny) = path[0]
            s.dx = nx - s.m.x
            s.dy = ny - s.m.y
            s.m.moveTo(nx, ny)
        }
    }

    /** Gölgelerin geçebildiği karelerden hedefe en kısa yol (hedef karesi her zaman girilebilir). */
    private fun shadowPath(sx: Int, sy: Int, tx: Int, ty: Int): List<Pair<Int, Int>>? {
        val start = sy * cols + sx
        val target = ty * cols + tx
        if (start == target) return emptyList()
        val prev = IntArray(cols * rows) { -1 }
        val seen = BooleanArray(cols * rows)
        val open = ArrayDeque<Int>()
        seen[start] = true
        open.add(start)
        while (open.isNotEmpty()) {
            val c = open.removeFirst()
            if (c == target) break
            for ((dx, dy) in DIRS) {
                val nx = c % cols + dx
                val ny = c / cols + dy
                if (nx !in 0 until cols || ny !in 0 until rows) continue
                val n = ny * cols + nx
                if (seen[n] || (n != target && !shadowCanEnter(nx, ny))) continue
                seen[n] = true
                prev[n] = c
                open.add(n)
            }
        }
        if (!seen[target]) return null
        val path = ArrayList<Pair<Int, Int>>()
        var c = target
        while (c != start) {
            path.add((c % cols) to (c / cols))
            c = prev[c]
        }
        path.reverse()
        return path
    }

    private fun arrive() {
        val tile = tiles[lumi.y][lumi.x]
        if (tile == Tile.THORN) {
            if (shields > 0) {
                // Işık Kalkanı: diken ışığı söndüremez, Lumi geldiği kareye geri sekip durur
                shields--
                shieldTime = SHIELD_TIME
                queue.clear()
                forced = null
                dirX = -dirX
                dirY = -dirY
                lumi.moveTo(lumi.fromX, lumi.fromY)
                onEvent(GameEvent.SHIELD)
                return
            }
            die()
            return
        }
        lit[lumi.y * cols + lumi.x] = true
        if (tile == Tile.LILY) onEvent(GameEvent.HOP)
        val i = shards.indexOfFirst { it.first == lumi.x && it.second == lumi.y }
        if (i >= 0) {
            shards.removeAt(i)
            collected++
            onEvent(if (energyFull) GameEvent.ENERGY_FULL else GameEvent.SHARD)
        }
        // Lumi şalterin yanına gelince Pıtır dar kovuğa girip çeker
        if (!bloomed && blooms.any { abs(it.first - lumi.x) + abs(it.second - lumi.y) == 1 }) {
            bloomed = true
            onEvent(GameEvent.BLOOM)
        }
        if (tile == Tile.CRYSTAL && !doorsOpen) {
            doorsOpen = true
            onEvent(GameEvent.CRYSTAL)
        }
        if (tile == Tile.EXIT && energyFull) {
            state = GameState.WON
            stateTime = 0f
            queue.clear()
            onEvent(GameEvent.WIN)
            return
        }
        // buzda aynı yönde kaymaya devam, rüzgâr karesinde rüzgârın yönüne itilme
        val (fx, fy) = when (tile) {
            Tile.ICE -> dirX to dirY
            Tile.WIND -> windDir(lumi.x, lumi.y)
            else -> return
        }
        if (forcedSteps < MAX_FORCED && lumiCanEnter(lumi.x + fx, lumi.y + fy, avoidThorns = false)) {
            forcedSteps++
            forced = fx to fy
            if (tile == Tile.WIND) onEvent(GameEvent.WIND)
        }
    }

    private fun die() {
        if (shields > 0) {
            shields--
            shieldTime = SHIELD_TIME
            onEvent(GameEvent.SHIELD)
            return
        }
        state = GameState.DYING
        stateTime = 0f
        queue.clear()
        forced = null
        onEvent(GameEvent.FAIL)
    }

    companion object {
        const val LUMI_STEP = 0.16f
        const val SLIDE_STEP = 0.09f
        const val MAX_FORCED = 40
        const val LANTERN_MOVES = 4
        const val BRIDGE_MOVES = 8
        const val SHIELD_TIME = 1.5f
        private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}
