package com.lumi.kayiprenkler

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Tek View: oyun döngüsü, dört ekran ve dokunma. Çizim parçaları [Art] içinde. */
class GameView(context: Context) : View(context) {

    private enum class Screen { MENU, SELECT, GAME, PROFILE }

    private class Button(val id: String, val rect: RectF, val enabled: Boolean, val action: () -> Unit)

    private val progress = Progress(context)
    private val sfx = Sfx(progress.soundOn)
    private val art = Art().apply { loadSprites(context.resources) }

    private var screen = Screen.MENU
    private var game: Game? = null
    private var showComplete = false
    private var completeTime = 0f

    // son bitirilen bölümün sonucu (tamamlandı kartı için)
    private var earnedStars = 0
    private var newRecord = false
    private var starSounds = 0
    private var energyShown = 0f
    private var lastNanos = 0L
    private var safeTop = 0f

    private val buttons = ArrayList<Button>()
    private var pressedId: String? = null
    private var downX = 0f
    private var downY = 0f

    private var u = 1f
    private var cell = 1f
    private var gridX = 0f
    private var gridY = 0f

    private val trLocale = Locale("tr", "TR")

    // Pıtır (yoldaş): ruh hâli, konuşma balonu, ipucu ve bağ
    private enum class Mood { CALM, HAPPY, SCARED }

    private var mood = Mood.CALM
    private var moodTime = 0f
    private var bubbleText = ""
    private var bubbleTime = 0f
    private var hintCell: Pair<Int, Int>? = null
    private var hintTime = 0f
    private var hintsLeft = 0
    private var idleHintShown = false
    private var petted = false
    private var petTime = 0f
    private var fails = 0
    private var greeted = false
    private var pitirTail = IntArray(0)
    private var pitirStage = 0

    /** Son bölümde kazanılan bağ puanı (bölüm sonu kartında ödül olarak). */
    private var lastBondGain = 0

    // Pıtır'ın bir hedefe (solmuş çiçek, Ruhsal Geçiş) koşup dönmesi ve yetenek dalgası
    private var fetchAnim = 0f
    private var fetchCell: Pair<Int, Int>? = null
    private var revealTime = 0f
    private var noticed = false

    // Lumi'nin pozu (0 yok, 1 zıplama, 2 etkileşim, 3 özel yetenek), geçen süresi; baktığı yön; Pıtır'ın dönüşü
    private var pose = 0
    private var poseTime = 0f
    private var face = 1f
    private var pitirSpin = 0f

    // Yakınlık: Pıtır Lumi'nin omzuna konar, kalpler yükselir. Bekleyince de kendiliğinden sokulur.
    private var hugTime = 0f

    // canavarlar: ilk karşılaşmada tanıtım kartı
    private val monsterIntro = ArrayDeque<Monster>()
    private var introTime = 0f
    private var idleHugDone = false

    // ---------------------------------------------------------------- yaşam döngüsü

    fun pause() {
        lastNanos = 0L
    }

    fun release() {
        sfx.release()
    }

    /** Android geri tuşu. false dönerse uygulama kapanır. */
    fun onBack(): Boolean {
        if (screen == Screen.MENU) return false
        goMenu()
        return true
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        safeTop = if (Build.VERSION.SDK_INT >= 28) {
            (insets.displayCutout?.safeInsetTop ?: 0).toFloat()
        } else 0f
        return super.onApplyWindowInsets(insets)
    }

    private fun openProfile() {
        markScreenChange()
        screen = Screen.PROFILE
        game = null
        showComplete = false
        profileHug = 0f
        profileTab = 0
    }

    private fun goMenu() {
        markScreenChange()
        screen = Screen.MENU
        game = null
        showComplete = false
    }

    private fun startLevel(i: Int) {
        markScreenChange()
        val bonus = if (i >= PITIR_FROM && progress.bondLevel >= 4) 0.3f else 0f
        val stage = if (i >= PITIR_FROM) stageFor(progress.bondLevel) else -1
        game = Game(i, bonus, stage, ::onGameEvent)
        showComplete = false
        energyShown = 0f
        screen = Screen.GAME
        setupPitir(i)
    }

    private fun hasPitir(g: Game) = g.index >= PITIR_FROM

    /** Bölüm başında Pıtır'ın görünüşü ve selamı. Dünya renklendikçe o da renklenir. */
    private fun setupPitir(i: Int) {
        mood = Mood.CALM
        moodTime = 0f
        bubbleTime = 0f
        hintTime = 0f
        hintCell = null
        idleHintShown = false
        petted = false
        petTime = 0f
        fails = 0
        fetchAnim = 0f
        fetchCell = null
        revealTime = 0f
        noticed = false
        pose = 0
        poseTime = 0f
        face = 1f
        pitirSpin = 0f
        hugTime = 0f
        idleHugDone = false
        monsterIntro.clear()
        introTime = 0f
        game?.let { g ->
            for (m in Monster.entries) if (m in g.monsterKinds && !progress.seenMonster(m)) {
                monsterIntro.add(m)
                progress.markMonster(m)
            }
        }
        if (monsterIntro.isNotEmpty()) introTime = INTRO_TIME
        if (i < PITIR_FROM) return
        hintsLeft = when {
            progress.bondLevel >= 5 -> 3
            progress.bondLevel >= 3 -> 2
            else -> 1
        }
        val done = progress.regionsDone()
        pitirTail = IntArray(5) { if (it < done) TAIL_COLORS[it] else 0 }
        pitirStage = stageFor(progress.bondLevel)
        val region = Levels.regions[Levels.regionOf(i)]
        when {
            i == PITIR_FROM && !progress.hasStar(i) -> say("Merhaba! Ben Pıtır. Seninle gelebilir miyim?", 3.5f)
            i == region.first || !greeted -> say(greeting(Levels.themeOf(i)), 3f)
        }
        greeted = true
        lastBondGain = 0
    }

    /** Bağ seviyesine göre görünüm: Tanışma/Arkadaş Başlangıç, Yol arkadaşı Orta, Can dostu İleri, Aile Ustalık. */
    private fun stageFor(bondLevel: Int) = (bondLevel - 2).coerceIn(0, 3)

    private fun greeting(t: Theme) = when (t) {
        Theme.FOREST -> "Mmm, çam kokusu! Haydi keşfedelim."
        Theme.LAKE -> "Nilüferlere dikkat, her biri bir kez taşır!"
        Theme.CAVE -> "Burası çok karanlık... yanından ayrılmam."
        Theme.SNOW -> "Atkımı taktım! Buzda kaymaya hazır mısın?"
        Theme.CLOUD -> "Kulaklarımı açarsam uçabilirim!"
    }

    private fun say(text: String, seconds: Float) {
        bubbleText = text
        bubbleTime = seconds
    }

    private fun feel(m: Mood, seconds: Float) {
        mood = m
        moodTime = seconds
    }

    /** Pıtır en yakın hedefi gösterir: parça, kristal ya da fidan. */
    private fun showHint(g: Game) {
        val t = g.hintTarget() ?: return
        hintsLeft--
        hintCell = t
        hintTime = 3.2f
        say(
            when (g.tiles[t.second][t.first]) {
                Tile.EXIT -> "Fidan parlıyor, oraya gidelim!"
                Tile.CRYSTAL -> "Kristale dokunursak kapılar açılır!"
                Tile.BLOOM -> "Solmuş çiçeğin yanına git, ona renk veririm!"
                else -> if (g.hiddenAt(t.first, t.second)) "Burada bir şey saklı. Düğmeme bas!"
                else "Bak, şurada bir ışık parçası var!"
            },
            2.6f,
        )
        feel(Mood.HAPPY, 0.8f)
    }

    /** Pıtır'a dokunmak: sevilir (bölümde bir kez bağ puanı), hakkı varsa ipucu verir. */
    private fun tapPitir(g: Game) {
        sfx.play("tap")
        petTime = 1f
        startHug(g)
        if (!petted) {
            petted = true
            progress.bond = progress.bond + 1
        }
        if (hintsLeft > 0 && g.state == GameState.PLAYING) {
            showHint(g)
        } else {
            say("Bu bölümde ipucum bitti, ama yanındayım!", 2f)
            feel(Mood.HAPPY, 0.8f)
        }
    }

    /** Yakınlık anı: Lumi Pıtır'a döner, Pıtır omzuna konar. */
    private fun startHug(g: Game) {
        if (hugTime > 0f) return
        hugTime = HUG_TIME
        val lx = gridX + (g.lumi.px + 0.5f) * cell
        val (px, _) = pitirPos(g)
        if (abs(px - lx) > 1f) face = if (px > lx) 1f else -1f
        feel(Mood.HAPPY, HUG_TIME)
    }

    private fun pitirReact(e: GameEvent, g: Game) {
        when (e) {
            GameEvent.SHARD -> feel(Mood.HAPPY, 0.8f)
            GameEvent.ENERGY_FULL -> { feel(Mood.HAPPY, 1.2f); say("Fidan parlıyor, hadi!", 2f) }
            GameEvent.CRYSTAL -> { feel(Mood.HAPPY, 1f); say("Kapılar açıldı!", 1.8f) }
            GameEvent.HUNTER -> { feel(Mood.SCARED, 2.5f); say("Eyvah, uyandı! Kaçalım!", 1.8f) }
            GameEvent.REVEAL -> {
                feel(Mood.HAPPY, 1.2f)
                revealTime = 1.2f
                hintCell = null
                say("Saklı olanlar, ortaya çıkın!", 2f)
            }
            GameEvent.LANTERN -> {
                feel(Mood.HAPPY, 1f)
                revealTime = 1.2f
                say("Kuyruğum ışık saçıyor!", 2f)
            }
            GameEvent.BLOOM -> {
                if (fetchAnim <= 0f) {
                    fetchAnim = 1.3f
                    fetchCell = (0 until g.rows).flatMap { y -> (0 until g.cols).map { x -> x to y } }
                        .firstOrNull { (x, y) -> g.tiles[y][x] == Tile.BLOOM }
                }
                hintCell = null
                feel(Mood.HAPPY, 1f)
                say("Renk Dokunuşu! Çiçek açtı, kökler çekildi.", 2.4f)
            }
            GameEvent.SHIELD -> {
                feel(Mood.HAPPY, 1f)
                revealTime = 1.2f
                say("Işık Kalkanı! Sana dokunamaz!", 2f)
            }
            GameEvent.LIGHT_BRIDGE -> {
                feel(Mood.HAPPY, 1f)
                revealTime = 1.2f
                say("Işık Köprüsü! Çabuk geç, uzun sürmez.", 2.2f)
            }
            GameEvent.SPIRIT -> {
                fetchAnim = 1.3f
                fetchCell = g.spiritTarget
                hintCell = null
                feel(Mood.HAPPY, 1.3f)
                say("Ruhsal Geçiş! Duvarlar beni durduramaz.", 2.4f)
            }
            GameEvent.FAIL -> {
                fails++
                feel(Mood.CALM, 0f)
                hintCell = null
                if (fails >= 3 && hintsLeft > 0) say("Bana dokun, sana yol göstereyim!", 3f)
                else say(ENCOURAGE[fails % ENCOURAGE.size], 2.2f)
            }
            GameEvent.WIN -> {
                feel(Mood.HAPPY, 99f)
                hintCell = null
                val before = progress.bondLevel
                lastBondGain = 1 + if (g.starsNow == 3) 1 else 0
                progress.bond = progress.bond + lastBondGain
                val after = progress.bondLevel
                if (after > before) {
                    pitirStage = stageFor(after)
                    revealTime = 1.2f
                    say("Bağımız güçlendi: ${Progress.BOND_NAMES[after - 1]}!", 2.5f)
                }
                else say("Yaşasın! Bir renk daha!", 2.2f)
            }
            else -> Unit
        }
    }

    private fun startPose(p: Int) {
        pose = p
        poseTime = 0f
    }

    private fun poseLength(p: Int) = when (p) {
        1 -> 0.45f
        2 -> 0.5f
        else -> 0.9f
    }

    private fun onGameEvent(e: GameEvent) {
        game?.takeIf { hasPitir(it) }?.let { pitirReact(e, it) }
        when (e) {
            GameEvent.HOP, GameEvent.WIND, GameEvent.WIN -> startPose(1)
            GameEvent.SHARD, GameEvent.ENERGY_FULL, GameEvent.CRYSTAL, GameEvent.BLOOM -> startPose(2)
            GameEvent.REVEAL, GameEvent.LANTERN, GameEvent.LIGHT_BRIDGE, GameEvent.SPIRIT, GameEvent.SHIELD -> {
                startPose(3)
                pitirSpin = 0.001f
            }
            else -> Unit
        }
        when (e) {
            GameEvent.STEP -> sfx.play("step")
            GameEvent.SHARD -> sfx.play("shard")
            GameEvent.ENERGY_FULL -> sfx.play("full")
            GameEvent.CRYSTAL -> sfx.play("crystal")
            GameEvent.FAIL -> sfx.play("fail")
            GameEvent.SPLASH -> sfx.play("splash")
            GameEvent.HUNTER -> sfx.play("hunter")
            GameEvent.WIND -> sfx.play("wind")
            GameEvent.REVEAL, GameEvent.BLOOM -> sfx.play("crystal")
            GameEvent.LANTERN, GameEvent.LIGHT_BRIDGE -> sfx.play("full")
            GameEvent.SHIELD -> sfx.play("crystal")
            GameEvent.SPIRIT -> sfx.play("shard")
            GameEvent.HOP -> sfx.play("step")
            GameEvent.WIN -> {
                sfx.play("win")
                game?.let {
                    earnedStars = it.starsNow
                    newRecord = progress.hasStar(it.index) && earnedStars > progress.stars(it.index)
                    progress.complete(it.index, earnedStars)
                    starSounds = 0
                }
            }
        }
    }

    // ---------------------------------------------------------------- döngü

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1e9f).coerceAtMost(0.05f)
        lastNanos = now
        art.time += dt
        u = min(width.toFloat(), height * 0.56f) / 100f
        art.u = u

        update(dt)
        buttons.clear()
        when (screen) {
            Screen.MENU -> drawMenu(canvas)
            Screen.SELECT -> drawSelect(canvas)
            Screen.PROFILE -> drawProfile(canvas)
            Screen.GAME -> drawGame(canvas)
        }
        postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        if (profileHug > 0f) profileHug -= dt
        if (screen == Screen.SELECT && abs(flingV) > 1f) {
            selectScroll = (selectScroll + flingV * dt).coerceIn(0f, maxSelectScroll())
            flingV *= kotlin.math.exp(-4.5f * dt)
        }
        val g = game ?: return
        if (screen != Screen.GAME) return
        energyShown += (g.collected - energyShown) * min(1f, dt * 8f)
        if (showComplete) {
            completeTime += dt
            return
        }
        g.update(dt)
        if (introTime > 0f) {
            introTime -= dt
            if (introTime <= 0f) {
                monsterIntro.removeFirstOrNull()
                if (monsterIntro.isNotEmpty()) introTime = INTRO_TIME
            }
        }
        if (pose != 0) {
            poseTime += dt
            if (poseTime >= poseLength(pose)) {
                // bölüm bitince sevinçle zıplamaya devam eder
                if (pose == 1 && g.state == GameState.WON) poseTime = 0f else pose = 0
            }
        }
        if (pitirSpin > 0f) {
            pitirSpin += dt / 0.8f
            if (pitirSpin >= 1f) pitirSpin = 0f
        }
        if (g.dirX != 0) face = g.dirX.toFloat()
        if (hasPitir(g)) {
            moodTime -= dt
            if (moodTime <= 0f) mood = Mood.CALM
            bubbleTime -= dt
            hintTime -= dt
            if (hintTime <= 0f) hintCell = null
            petTime -= dt
            if (hugTime > 0f) hugTime -= dt
            // oyuncu bir süre durunca Pıtır kendiliğinden sokulur
            if (g.idle < 1f) idleHugDone = false
            if (!idleHugDone && g.state == GameState.PLAYING && g.idle > IDLE_HUG && g.lumi.t >= 1f) {
                idleHugDone = true
                startHug(g)
            }
            fetchAnim -= dt
            revealTime -= dt
            if (!noticed && g.state == GameState.PLAYING && g.secretNear(g.lumi.x, g.lumi.y, 2)) {
                noticed = true
                feel(Mood.HAPPY, 1f)
                say("Renk Görüşü: burada saklı bir şey var!", 2.4f)
            }
            if (g.state == GameState.PLAYING && g.idle > IDLE_HINT && !idleHintShown && hintsLeft > 0) {
                idleHintShown = true
                showHint(g)
            }
        }
        if (g.state == GameState.DYING && g.stateTime > 1.1f) g.restart()
        if (g.state == GameState.WON && g.stateTime > 1.9f) {
            showComplete = true
            completeTime = 0f
        }
    }

    // ---------------------------------------------------------------- dokunma

    private fun button(id: String, r: RectF, enabled: Boolean = true, action: () -> Unit) {
        buttons.add(Button(id, r, enabled, action))
    }

    private fun isPressed(id: String) = pressedId == id

    private fun roundRect(cx: Float, cy: Float, rad: Float) = RectF(cx - rad, cy - rad, cx + rad, cy + rad)

    private var screenChangedAt = 0L
    private var ignoreGesture = false

    private fun markScreenChange() {
        screenChangedAt = android.os.SystemClock.uptimeMillis()
        pressedId = null
        dragging = false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (ignoreGesture && e.actionMasked != MotionEvent.ACTION_DOWN) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // ekran yeni değiştiyse aynı dokunuşun yeni ekrana "geçmesini" engelle
                ignoreGesture = e.eventTime - screenChangedAt < 250
                if (ignoreGesture) return true
                downX = e.x
                downY = e.y
                pressedId = hitButton(e.x, e.y)?.takeIf { it.enabled }?.id
                scrollAtDown = selectScroll
                flingV = 0f
                dragging = false
                moveVel = 0f
                lastMoveY = e.y
                lastMoveT = e.eventTime
            }
            MotionEvent.ACTION_MOVE -> if (screen == Screen.SELECT) {
                val dy = e.y - downY
                if (!dragging && abs(dy) > 2.5f * u && downY > mapTop - 2 * u) {
                    dragging = true
                    pressedId = null
                }
                if (dragging) {
                    selectScroll = (scrollAtDown - dy).coerceIn(0f, maxSelectScroll())
                    val dt = (e.eventTime - lastMoveT).coerceAtLeast(1L) / 1000f
                    moveVel = 0.6f * moveVel + 0.4f * ((e.y - lastMoveY) / dt)
                    lastMoveY = e.y
                    lastMoveT = e.eventTime
                }
            }
            MotionEvent.ACTION_UP -> {
                val pressed = pressedId
                pressedId = null
                if (dragging) {
                    dragging = false
                    flingV = -moveVel
                } else if (pressed != null) {
                    val b = hitButton(e.x, e.y)
                    if (b != null && b.id == pressed && b.enabled) {
                        sfx.play("tap")
                        b.action()
                    }
                } else if (screen == Screen.GAME && !showComplete) {
                    gridGesture(e.x, e.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> pressedId = null
        }
        return true
    }

    private fun hitButton(x: Float, y: Float): Button? = buttons.lastOrNull { it.rect.contains(x, y) }

    private fun gridGesture(x: Float, y: Float) {
        val g = game ?: return
        val dx = x - downX
        val dy = y - downY
        if (hypot(dx, dy) > 6 * u) {
            if (abs(dx) > abs(dy)) g.swipe(if (dx > 0) 1 else -1, 0)
            else g.swipe(0, if (dy > 0) 1 else -1)
            return
        }
        val cx = floor((x - gridX) / cell).toInt()
        val cy = floor((y - gridY) / cell).toInt()
        if (hasPitir(g) && cx == g.pitir.x && cy == g.pitir.y && (cx != g.lumi.x || cy != g.lumi.y)) {
            tapPitir(g)
            return
        }
        if (cx in 0 until g.cols && cy in 0 until g.rows) g.tapCell(cx, cy)
    }

    // ---------------------------------------------------------------- ekran 1: ana menü

    /**
     * Ana menü: üstte karakter sayfasının sahnesi (Lumi, Pıtır, logo ve söz), altta gece mavisine geçiş ve
     * ışıklı düğmeler. Sahne görseli yüklenemezse düz gece mavisi.
     */
    private fun drawMenu(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        art.vGradient(c, 0f, 0f, w, h, 0xFF0B1730.toInt(), 0xFF16305A.toInt())
        val hero = art.hero
        // görsel kenarlardan kesilmesin: tam genişlik, kendi oranında
        val heroH = if (hero != null) min(h * 0.68f, w * hero.height / hero.width) else h * 0.5f
        if (hero != null) {
            art.image(c, hero, RectF(0f, 0f, w, heroH), 0f)
            val under = art.mix(0xFF0B1730.toInt(), 0xFF16305A.toInt(), heroH / h)
            art.vGradient(c, 0f, heroH * 0.7f, w, heroH + 1f, under and 0x00FFFFFF, under)
        }
        art.twinkles(c, w, h, 0.35f)
        val t = art.time
        for (i in 0 until 6) {
            val fx = w * (0.5f + 0.45f * sin(t * 0.21f + i * 1.9f))
            val fy = heroH * (0.55f + 0.3f * sin(t * 0.29f + i * 2.7f))
            art.firefly(c, fx, fy, 0.38f * u, 0.8f)
        }

        val bw = 70 * u
        val bh = 10.5f * u
        val gap = 3.2f * u
        val x0 = (w - bw) / 2
        // düğmeler görselin hemen altında; en altta alt yazı
        var y = heroH + 3 * u
        val next = progress.nextLevel()
        val items = listOf(
            Triple("play", if (progress.starCount() == 0) "Oyuna Başla" else "Devam Et", true),
            Triple("levels", "Bölüm Seç", false),
            Triple("profile", "Karakter ve Yoldaş", false),
            Triple("sound", if (sfx.enabled) "Ses: Açık" else "Ses: Kapalı", false),
        )
        for ((id, label, primary) in items) {
            val r = RectF(x0, y, x0 + bw, y + bh)
            button(id, r) {
                when (id) {
                    "play" -> if (next >= 0) startLevel(next) else openSelect()
                    "levels" -> openSelect()
                    "profile" -> openProfile()
                    else -> {
                        sfx.enabled = !sfx.enabled
                        progress.soundOn = sfx.enabled
                        sfx.play("tap")
                    }
                }
            }
            art.glowButton(c, r, label, primary, isPressed(id))
            y += bh + gap
        }
        art.smallStar(c, w / 2 - 7 * u, y + 2.5f * u, 1.5f * u, true)
        art.text(c, "${progress.starCount()} / ${progress.maxStars}", w / 2 + 1.5f * u, y + 2.5f * u, 3.1f * u, 0xCCDFFBFF.toInt())
        val fy = max(y + 12 * u, h - 9 * u)
        art.stroke.color = 0x557FE6F0
        art.stroke.strokeWidth = 0.25f * u
        c.drawLine(8 * u, fy - 5 * u, w / 2 - 22 * u, fy - 5 * u, art.stroke)
        c.drawLine(w / 2 + 22 * u, fy - 5 * u, w - 8 * u, fy - 5 * u, art.stroke)
        art.title(c, "Lumi · Kayıp Renkler", w / 2, fy - 5 * u, 3.6f * u, 0xCCB8E8F5.toInt())
        art.text(c, "Renkleri geri getir, dünyayı yeniden canlandır...", w / 2, fy, 2.6f * u, 0x99B8E8F5.toInt())
    }

    private fun reeds(c: Canvas, x: Float, baseY: Float) {
        art.stroke.color = 0xFF2C3A5A.toInt()
        art.stroke.strokeWidth = 0.5f * u
        for (i in -1..1) {
            val tx = x + i * 1.8f * u + sin(art.time + i) * 0.4f * u
            val ty = baseY - (14 + i * 3) * u
            c.drawLine(x + i * u, baseY, tx, ty, art.stroke)
            art.fill.color = 0xFF4A3A35.toInt()
            art.rect.set(tx - 0.9f * u, ty + 0.5f * u, tx + 0.9f * u, ty + 5 * u)
            c.drawRoundRect(art.rect, u, u, art.fill)
        }
    }

    // ---------------------------------------------------------------- ekran 5: Lumi (karakter ve yoldaş)

    private var profileHug = 0f

    private class Detail(val name: String, val sub: String)

    private val details = listOf(
        Detail("Saç aksesuarı", "renk kristali"),
        Detail("Kolye", "enerji kaynağı"),
        Detail("Çanta", "keşif eşyaları"),
        Detail("Pelerin detayı", "ışık sembolü"),
    )

    /**
     * Lumi'nin sayfası: tam boy Lumi ve yanında Pıtır, kişiliği, renk paleti, detay kartları (saç aksesuarı, kolye,
     * çanta, pelerin) ve Pıtır'la bağ: seviye, kalpler, sonraki seviyeye kalan puan, açılan yetenekler.
     * Lumi'ye ya da Pıtır'a dokununca yakınlık anı oynar. Kısa ekranlarda içerik orantılı küçülür.
     */
    private var profileTab = 0

    /** Karakter ve Yoldaş sayfaları; altta iki sekme. */
    private fun drawProfile(c: Canvas) {
        if (profileTab == 0) drawProfileLumi(c) else drawProfilePitir(c)
        drawProfileTabs(c)
    }

    private fun drawProfileTabs(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bar = RectF(4 * u, h - 15 * u, w - 4 * u, h - 2 * u)
        art.glass(c, bar, 4 * u, glowing = true)
        for (k in 0..1) {
            val x0 = bar.left + bar.width() * k / 2
            val r = RectF(x0 + u, bar.top + u, x0 + bar.width() / 2 - u, bar.bottom - u)
            button("tab$k", r) { profileTab = k }
            if (profileTab == k) {
                art.fill.color = 0x447FE6F0
                c.drawRoundRect(r, 3 * u, 3 * u, art.fill)
            }
            val ix = r.left + 9 * u
            if (k == 0) art.lumiPlay(c, ix, r.centerY() - 0.6f * u, 2.1f * u, 1f, 1f, walking = false, pose = 0, poseT = 0f)
            else art.pitir(c, ix, r.centerY() + 0.8f * u, 2.3f * u, 1f, IntArray(0), stageFor(progress.bondLevel), 0, 0f, false)
            art.text(c, if (k == 0) "Karakter" else "Yoldaş", ix + 5 * u, r.centerY(), 3.4f * u,
                if (profileTab == k) Color.WHITE else 0xAADFFBFF.toInt(), android.graphics.Paint.Align.LEFT)
        }
    }

    /** Yoldaş sayfası: Pıtır'ın tanıtım görseli, bağ seviyesi ve her yeteneğin açık ya da kilitli durumu. */
    private fun drawProfilePitir(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        art.vGradient(c, 0f, 0f, w, h, 0xFF0B1730.toInt(), 0xFF16305A.toInt())
        art.twinkles(c, w, h, 0.4f)
        val top = safeTop
        val bx = 9.5f * u
        val bcy = top + 10 * u
        button("back", roundRect(bx, bcy, 6 * u)) { goMenu() }
        art.ghostButton(c, bx, bcy, 5.5f * u, isPressed("back"))
        art.iconBack(c, bx, bcy, 6 * u, Color.WHITE)
        art.title(c, "Yoldaş", w / 2, top + 10 * u, 7 * u, 0xFFDFFBFF.toInt())

        val s = min(1f, (h - top - 17 * u) / (214 * u))
        c.save()
        c.scale(s, s, w / 2, top)
        val met = progress.isUnlocked(PITIR_FROM)
        val stage = stageFor(progress.bondLevel)
        val banner = RectF(4 * u, top + 19 * u, w - 4 * u, top + 74 * u)
        val img = art.pitirHero
        if (img != null) {
            art.image(c, img, banner, 4 * u)
            // görseldeki konuşma balonunun üstüne Pıtır'ın kendi balonu
            val bub = RectF(
                banner.left + banner.width() * 0.545f, banner.top + banner.height() * 0.3f,
                banner.left + banner.width() * 0.885f, banner.top + banner.height() * 0.74f,
            )
            art.fill.color = 0xF0E8FBFF.toInt()
            c.drawOval(bub, art.fill)
            art.stroke.color = 0xFF7FE6F0.toInt()
            art.stroke.strokeWidth = 0.4f * u
            c.drawOval(bub, art.stroke)
            art.wrapped(c, "Merhaba, ben Pıtır! Renkleri birlikte geri getirelim!", bub.centerX(), bub.centerY(), bub.width() * 0.72f, 2.8f * u, 0xFF16305A.toInt())
        }
        art.stroke.color = 0x887FE6F0.toInt()
        art.stroke.strokeWidth = 0.35f * u
        c.drawRoundRect(banner, 4 * u, 4 * u, art.stroke)

        val info = RectF(4 * u, top + 77 * u, w - 4 * u, top + 96 * u)
        art.glass(c, info, 4 * u)
        art.title(c, "Pıtır", info.left + 4 * u, info.top + 5.5f * u, 5 * u, Color.WHITE, android.graphics.Paint.Align.LEFT)
        val lvl = progress.bondLevel
        art.text(c, "Lv. $lvl · ${Progress.BOND_NAMES[lvl - 1]} · ${STAGE_NAMES[stage]}", info.left + 26 * u, info.top + 5.5f * u, 2.9f * u, 0xFFB8E8F5.toInt(), android.graphics.Paint.Align.LEFT)
        val bond = progress.bond
        val cur = Progress.BOND_STEPS[lvl - 1]
        val nxt = Progress.BOND_STEPS.getOrNull(lvl)
        val bar = RectF(info.left + 4 * u, info.top + 11.5f * u, info.right - 18 * u, info.top + 14.3f * u)
        art.fill.color = 0x33FFFFFF
        c.drawRoundRect(bar, 1.4f * u, 1.4f * u, art.fill)
        val f = if (nxt == null) 1f else ((bond - cur).toFloat() / (nxt - cur)).coerceIn(0f, 1f)
        if (f > 0f) {
            art.shader.shader = android.graphics.LinearGradient(bar.left, 0f, bar.right, 0f, 0xFF7FE6F0.toInt(), 0xFF4FA8E8.toInt(), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * f, bar.bottom), 1.4f * u, 1.4f * u, art.shader)
            art.shader.shader = null
        }
        art.text(c, if (nxt == null) "En yüksek" else "$bond / $nxt", info.right - 4 * u, bar.centerY(), 2.6f * u, 0xFFB8E8F5.toInt(), android.graphics.Paint.Align.RIGHT)

        art.text(c, "YETENEKLER", 6 * u, top + 101 * u, 2.8f * u, 0xFF7FE6F0.toInt(), android.graphics.Paint.Align.LEFT, spacing = 0.18f)
        for ((k, ab) in ABILITIES.withIndex()) {
            val y0 = top + 104 * u + k * 13.2f * u
            val row = RectF(4 * u, y0, w - 4 * u, y0 + 11.6f * u)
            val open = met && stage >= ab.stage
            art.glass(c, row, 3 * u, alpha = if (open) 1f else 0.6f)
            val icx = row.left + 6.5f * u
            art.glow(c, icx, row.centerY(), 5 * u, ab.color, if (open) 0.45f else 0.1f)
            art.fill.color = if (open) ab.color else 0xFF3A4A66.toInt()
            c.drawCircle(icx, row.centerY(), 3.6f * u, art.fill)
            art.stroke.color = 0xCCFFFFFF.toInt()
            art.stroke.strokeWidth = 0.3f * u
            c.drawCircle(icx, row.centerY(), 3.6f * u, art.stroke)
            art.diamond(c, icx, row.centerY(), 1.4f * u, Color.WHITE)
            art.text(c, ab.name, row.left + 13 * u, row.top + 4 * u, 3.2f * u, if (open) Color.WHITE else 0x99FFFFFF.toInt(), android.graphics.Paint.Align.LEFT)
            art.text(c, ab.desc, row.left + 13 * u, row.top + 8.4f * u, 2.5f * u, if (open) 0xCCB8E8F5.toInt() else 0x77B8E8F5, android.graphics.Paint.Align.LEFT)
            if (open) {
                art.text(c, "Açık", row.right - 4 * u, row.centerY(), 2.7f * u, 0xFF8CF5B0.toInt(), android.graphics.Paint.Align.RIGHT)
            } else {
                art.lock(c, row.right - 6 * u, row.centerY() - 0.8f * u, 3.4f * u, 0x99FFFFFF.toInt())
                art.text(c, STAGE_NAMES[ab.stage], row.right - 6 * u, row.centerY() + 3.2f * u, 2f * u, 0x99FFFFFF.toInt())
            }
        }
        c.restore()
    }

    private fun drawProfileLumi(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = art.time
        art.vGradient(c, 0f, 0f, w, h, 0xFF0B1730.toInt(), 0xFF16305A.toInt())
        art.twinkles(c, w, h, 0.4f)
        val top = safeTop

        val bx = 9.5f * u
        val bcy = top + 10 * u
        button("back", roundRect(bx, bcy, 6 * u)) { goMenu() }
        art.ghostButton(c, bx, bcy, 5.5f * u, isPressed("back"))
        art.iconBack(c, bx, bcy, 6 * u, Color.WHITE)
        art.glow(c, w / 2, top + 8 * u, 18 * u, 0xFF9CEFF4.toInt(), 0.18f)
        art.title(c, "Lumi", w / 2, top + 8 * u, 8 * u, 0xFFDFFBFF.toInt())
        art.text(c, "“Renkler kaybolsa da umut hep bir yerde saklıdır...”", w / 2, top + 15.5f * u, 2.7f * u, 0xCCE6E0FF.toInt())

        // kısa ekranda içerik sığsın
        val s = min(1f, (h - top - 17 * u) / (224 * u))
        fun sx(x: Float) = w / 2 + (x - w / 2) * s
        fun sy(y: Float) = top + (y - top) * s
        c.save()
        c.scale(s, s, w / 2, top)

        // --- Lumi ve Pıtır
        val hero = RectF(4 * u, top + 21 * u, w - 4 * u, top + 84 * u)
        panel(c, hero)
        val lx = 24 * u
        val ly = top + 56 * u
        art.glow(c, lx, ly - 4 * u, 24 * u, 0xFFFFE7A0.toInt(), 0.25f)
        art.lumi(c, lx, ly, 13.5f * u, 0f, 0f)

        val met = progress.isUnlocked(PITIR_FROM)
        val stage = stageFor(progress.bondLevel)
        if (met) {
            val ht = if (profileHug > 0f) 1f - profileHug / HUG_TIME else 0f
            val k = if (profileHug > 0f) (sin(ht * Math.PI.toFloat()) * 1.8f).coerceIn(0f, 1f) else 0f
            val px = 41 * u + (31 * u - 41 * u) * k
            val py = top + 50 * u + sin(t * 2f) * 1.2f * u * (1f - k) + (top + 39 * u - top - 50 * u) * k
            if (k > 0f) {
                art.glow(c, (lx + px) / 2, py, 14 * u, 0xFFFFB8E0.toInt(), 0.3f * k)
                for (j in 0 until 4) art.heart(c, px - 2 * u + j * 1.6f * u, py - 5 * u, 2.4f * u, ht * 1.3f - j * 0.15f)
            }
            art.pitir(c, px, py, 4.6f * u, -1f, IntArray(0), stage, if (k > 0f) 1 else 0, 0f, false)
            val hit = RectF(sx(px - 7 * u), sy(py - 8 * u), sx(px + 7 * u), sy(py + 7 * u))
            button("hug", hit) { if (profileHug <= 0f) profileHug = HUG_TIME }
        }
        button("hugLumi", RectF(sx(lx - 8 * u), sy(ly - 28 * u), sx(lx + 8 * u), sy(ly + 24 * u))) {
            if (met && profileHug <= 0f) profileHug = HUG_TIME
        }

        // kişilik
        val kx = 52 * u
        art.text(c, "KİŞİLİK", kx, top + 28 * u, 2.8f * u, 0xFFB9AEE0.toInt(), android.graphics.Paint.Align.LEFT, spacing = 0.18f)
        for ((i, word) in listOf("Cesur", "Meraklı", "İyimser", "Kararlı").withIndex()) {
            val y0 = top + 31.5f * u + i * 8 * u
            art.rect.set(kx, y0, w - 8 * u, y0 + 6.2f * u)
            art.fill.color = 0x26FFFFFF
            c.drawRoundRect(art.rect, 3.1f * u, 3.1f * u, art.fill)
            art.diamond(c, kx + 3.4f * u, y0 + 3.1f * u, 1.1f * u, 0xFF9CEFF4.toInt())
            art.text(c, word, kx + 6.5f * u, y0 + 3.1f * u, 3.2f * u, Color.WHITE, android.graphics.Paint.Align.LEFT)
        }
        // renk paleti
        art.text(c, "RENK PALETİ", kx, top + 67.5f * u, 2.8f * u, 0xFFB9AEE0.toInt(), android.graphics.Paint.Align.LEFT, spacing = 0.18f)
        val palette = intArrayOf(0xFF4FD8E0.toInt(), 0xFFF2EEE6.toInt(), 0xFFD9B26A.toInt(), 0xFFC49BEA.toInt(), 0xFF7A6470.toInt(), 0xFF9C7A8C.toInt())
        for ((i, col) in palette.withIndex()) {
            art.fill.color = col
            c.drawCircle(kx + 2.3f * u + i * 6.4f * u, top + 74.5f * u, 2.3f * u, art.fill)
        }

        // --- detaylar
        sectionTitle(c, "DETAYLAR", top + 91 * u, w)
        val cw = (w - 12 * u) / 2
        for ((i, d) in details.withIndex()) {
            val x0 = 4 * u + (i % 2) * (cw + 4 * u)
            val y0 = top + 95 * u + (i / 2) * 38 * u
            val card = RectF(x0, y0, x0 + cw, y0 + 35 * u)
            panel(c, card)
            val img = art.details.getOrNull(i)
            val ir = RectF(x0 + 1.5f * u, y0 + 1.5f * u, x0 + cw - 1.5f * u, y0 + 23 * u)
            if (img != null) art.image(c, img, ir, 2.5f * u)
            art.text(c, d.name, x0 + cw / 2, y0 + 27.3f * u, 3.2f * u, Color.WHITE)
            art.text(c, "(${d.sub})", x0 + cw / 2, y0 + 31.6f * u, 2.6f * u, 0xFFB9AEE0.toInt())
        }

        // --- yoldaş
        sectionTitle(c, "YOLDAŞ · PITIR", top + 176 * u, w)
        val pr = RectF(4 * u, top + 180 * u, w - 4 * u, top + 222 * u)
        panel(c, pr)
        if (!met) {
            art.text(c, "Pıtır'la 3. bölümde tanışacaksın.", w / 2, pr.centerY(), 3.3f * u, 0xDDFFFFFF.toInt())
        } else {
            art.pitir(c, 14 * u, top + 199 * u, 4 * u, 1f, IntArray(0), stage, 0, 0f, false)
            val lvl = progress.bondLevel
            val x0 = 26 * u
            art.text(c, "${Progress.BOND_NAMES[lvl - 1]} · ${STAGE_NAMES[stage]}", x0, top + 186 * u, 3.4f * u, Color.WHITE, android.graphics.Paint.Align.LEFT)
            for (k in 0 until 5) {
                art.text(c, "♥", x0 + 1.6f * u + k * 4.4f * u, top + 192 * u, 3.6f * u, if (k < lvl) 0xFFFF7A95.toInt() else 0x44FFFFFF)
            }
            val bond = progress.bond
            val cur = Progress.BOND_STEPS[lvl - 1]
            val nxt = Progress.BOND_STEPS.getOrNull(lvl)
            val bar = RectF(x0, top + 196.5f * u, w - 8 * u, top + 199.3f * u)
            art.fill.color = 0x33FFFFFF
            c.drawRoundRect(bar, 1.4f * u, 1.4f * u, art.fill)
            val f = if (nxt == null) 1f else ((bond - cur).toFloat() / (nxt - cur)).coerceIn(0f, 1f)
            if (f > 0f) {
                art.fill.color = 0xFFFF8FB8.toInt()
                c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * f, bar.bottom), 1.4f * u, 1.4f * u, art.fill)
            }
            art.text(
                c, if (nxt == null) "En yüksek bağ" else "$bond / $nxt", w - 8 * u, top + 192 * u, 2.7f * u,
                0xFFB9AEE0.toInt(), android.graphics.Paint.Align.RIGHT,
            )
            val open = ABILITY_STAGES.take(stage + 1).flatten().joinToString(", ")
            art.wrapped(c, "Açık: $open", (x0 + w - 8 * u) / 2, top + 206 * u, w - 8 * u - x0, 2.6f * u, 0xE6FFFFFF.toInt())
            val nextStage = ABILITY_STAGES.getOrNull(stage + 1)
            if (nextStage != null) {
                val need = Progress.BOND_STEPS[stage + 2] - bond
                art.text(
                    c, "${STAGE_NAMES[stage + 1]}: ${nextStage.joinToString(", ")} · $need puan", (x0 + w - 8 * u) / 2,
                    top + 216.5f * u, 2.4f * u, 0xFFB9AEE0.toInt(),
                )
            }
        }
        c.restore()
    }

    private fun panel(c: Canvas, r: RectF) = art.glass(c, r, 4 * u)

    private fun sectionTitle(c: Canvas, s: String, y: Float, w: Float) {
        art.diamond(c, 6.5f * u, y, 1f * u, 0xFF9CEFF4.toInt())
        art.text(c, s, 9.5f * u, y, 3.2f * u, Color.WHITE, android.graphics.Paint.Align.LEFT, spacing = 0.18f)
        art.stroke.color = 0x33FFFFFF
        art.stroke.strokeWidth = 0.25f * u
        c.drawLine(w / 2 + 8 * u, y, w - 4 * u, y, art.stroke)
    }

    // ---------------------------------------------------------------- ekran 4: bölüm seçimi (harita)

    // Harita kaydırılabilir: her bölge bir blok, 10 bölüm iki sırada yılan gibi dizilir.
    private var selectScroll = 0f
    private var scrollAtDown = 0f
    private var flingV = 0f
    private var dragging = false
    private var lastMoveY = 0f
    private var lastMoveT = 0L
    private var moveVel = 0f

    private val nodeCols = floatArrayOf(14f, 32f, 50f, 68f, 86f)
    private val blockH get() = 64 * u
    private val mapTop get() = safeTop + 30 * u

    private fun nodeX(j: Int) = nodeCols[if (j < 5) j else 9 - j] * u
    private fun nodeY(j: Int) = (if (j < 5) 31f else 48f) * u

    private fun maxSelectScroll(): Float =
        max(0f, blockH * Levels.regions.size + 4 * u - (height - mapTop))

    /** Haritayı açar ve sıradaki bölümün bölgesini görünür yapar. */
    private fun openSelect() {
        markScreenChange()
        screen = Screen.SELECT
        game = null
        showComplete = false
        flingV = 0f
        val next = progress.nextLevel()
        val region = if (next >= 0) Levels.regionOf(next) else Levels.regions.size - 1
        selectScroll = (region * blockH - 2 * u).coerceIn(0f, maxSelectScroll())
    }

    private fun drawSelect(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        art.vGradient(c, 0f, 0f, w, h, 0xFF0B1730.toInt(), 0xFF16305A.toInt())
        art.twinkles(c, w, h, 0.5f)

        selectScroll = selectScroll.coerceIn(0f, maxSelectScroll())
        val clipTop = mapTop - 2 * u
        val next = progress.nextLevel()
        c.save()
        c.clipRect(0f, clipTop, w, h)
        for ((k, region) in Levels.regions.withIndex()) {
            val yk = mapTop + k * blockH - selectScroll
            if (yk > h || yk + blockH < clipTop) continue
            drawRegion(c, k, region, yk, next, clipTop)
            if (k < Levels.regions.size - 1) {
                art.stroke.color = 0x40FFFFFF
                art.stroke.strokeWidth = 0.4f * u
                c.drawLine(6 * u, yk + 59 * u, w - 6 * u, yk + 59 * u, art.stroke)
            }
        }
        c.restore()

        // sabit başlık; altındaki içerik kayarken üstte kalır
        val top = safeTop + 5 * u
        art.vGradient(c, 0f, 0f, w, clipTop, 0xFF0B1730.toInt(), art.mix(0xFF0B1730.toInt(), 0xFF16305A.toInt(), clipTop / h))
        art.fill.color = 0x22000000
        c.drawRect(0f, clipTop - 0.4f * u, w, clipTop, art.fill)
        val bx = 9.5f * u
        val bcy = top + 5.5f * u
        button("back", roundRect(bx, bcy, 6 * u)) { goMenu() }
        art.ghostButton(c, bx, bcy, 5.5f * u, isPressed("back"))
        art.iconBack(c, bx, bcy, 6 * u, Color.WHITE)

        art.title(c, "Bölüm Seç", 18 * u, top + 5.5f * u, 5.6f * u, 0xFFDFFBFF.toInt(), android.graphics.Paint.Align.LEFT)
        val chip = RectF(w - 30 * u, top + 1.5f * u, w - 4 * u, top + 9.5f * u)
        art.glass(c, chip, 4 * u)
        art.smallStar(c, chip.left + 4.5f * u, chip.centerY(), 1.7f * u, true)
        art.text(c, "${progress.starCount()}/${progress.maxStars}", chip.left + 8 * u, chip.centerY(), 3.1f * u, Color.WHITE, android.graphics.Paint.Align.LEFT)
        art.text(c, "Renk kristalleri · ${Levels.regions.count { r -> (r.first..r.last).all { progress.hasStar(it) } }}/${Levels.regions.size} bölge", w / 2, top + 17 * u, 2.9f * u, 0xBBB8E8F5.toInt())
    }

    private fun drawRegion(c: Canvas, k: Int, region: Region, yk: Float, next: Int, clipTop: Float) {
        val active = progress.isUnlocked(region.first)
        val w = width.toFloat()
        art.glass(c, RectF(3 * u, yk + 0.5f * u, w - 3 * u, yk + 57.5f * u), 4 * u, alpha = if (active) 1f else 0.6f)
        // bölge sahnesi: karakter sayfasındaki "Oyun İçinde Lumi" görselleri; yoksa eski altıgen manzara
        val scene = art.scenes.getOrNull(k.coerceAtMost(art.scenes.size - 1))
        if (scene != null) {
            val ir = RectF(5 * u, yk + 2.5f * u, 24 * u, yk + 21.5f * u)
            art.image(c, scene, ir, 3 * u)
            if (!active) {
                art.fill.color = 0xAA0B1730.toInt()
                c.drawRoundRect(ir, 3 * u, 3 * u, art.fill)
                art.lock(c, ir.centerX(), ir.centerY(), 5 * u, 0xEEFFFFFF.toInt())
            }
            art.stroke.color = 0x997FE6F0.toInt()
            art.stroke.strokeWidth = 0.3f * u
            c.drawRoundRect(ir, 3 * u, 3 * u, art.stroke)
            art.fill.color = 0xFF2E6FD0.toInt()
            c.drawCircle(ir.left + 1.5f * u, ir.top + 1.5f * u, 3 * u, art.fill)
            art.stroke.color = 0xFFC8FBFF.toInt()
            c.drawCircle(ir.left + 1.5f * u, ir.top + 1.5f * u, 3 * u, art.stroke)
            art.text(c, "${k + 1}", ir.left + 1.5f * u, ir.top + 1.5f * u, 3.4f * u, Color.WHITE)
        } else {
            regionHex(c, 13 * u, yk + 12 * u, 10 * u, k, active)
        }
        art.text(c, region.name, 27 * u, yk + 8.5f * u, 4.4f * u, if (active) Color.WHITE else 0x99FFFFFF.toInt(), android.graphics.Paint.Align.LEFT, spacing = 0.2f)

        art.stroke.pathEffect = DashPathEffect(floatArrayOf(u, u * 1.2f), 0f)
        art.stroke.strokeWidth = 0.45f * u
        for (j in 0 until region.count - 1) {
            art.stroke.color = if (progress.isUnlocked(region.first + j + 1)) 0x99FFFFFF.toInt() else 0x33FFFFFF
            c.drawLine(nodeX(j), yk + nodeY(j), nodeX(j + 1), yk + nodeY(j + 1), art.stroke)
        }
        art.stroke.pathEffect = null

        for (j in 0 until region.count) {
            val i = region.first + j
            val lv = Levels.all[i]
            val nx = nodeX(j)
            val ny = yk + nodeY(j)
            val r = 6.8f * u
            val unlocked = progress.isUnlocked(i)
            val done = progress.hasStar(i)
            if (ny > clipTop + r * 0.5f && ny < height) {
                button("lvl$i", roundRect(nx, ny, r), unlocked) { startLevel(i) }
            }
            val pr = if (isPressed("lvl$i")) r * 0.92f else r
            if (i == next) art.glow(c, nx, ny, r * 2.2f, 0xFFFFE08A.toInt(), 0.35f + 0.2f * sin(art.time * 3f))
            when {
                done -> {
                    art.glow(c, nx, ny, r * 1.8f, lv.accent, 0.4f)
                    art.fill.color = Color.WHITE
                    c.drawPath(art.hexPath(nx, ny, pr), art.fill)
                    art.fill.color = lv.accent
                    c.drawPath(art.hexPath(nx, ny, pr * 0.84f), art.fill)
                    art.text(c, "${i + 1}", nx, ny, 5 * u, Color.WHITE, shadow = 0x55000000)
                    val got = progress.stars(i)
                    for (k in 0 until 3) {
                        val sx = nx + (k - 1) * 2.9f * u
                        val sy = ny + r * 1.02f + if (k == 1) 0.5f * u else 0f
                        if (k < got) {
                            art.smallStar(c, sx, sy, 1.55f * u, true)
                        } else {
                            art.fill.color = 0x553A2D5C
                            c.drawPath(art.starPath(sx, sy, 1.55f * u), art.fill)
                        }
                    }
                }
                unlocked -> {
                    art.fill.color = 0xFFC8FBFF.toInt()
                    c.drawPath(art.hexPath(nx, ny, pr), art.fill)
                    art.fill.color = 0xFF1C3A66.toInt()
                    c.drawPath(art.hexPath(nx, ny, pr * 0.86f), art.fill)
                    art.text(c, "${i + 1}", nx, ny, 5 * u, Color.WHITE)
                }
                else -> {
                    art.fill.color = 0xFF231B3A.toInt()
                    c.drawPath(art.hexPath(nx, ny, pr), art.fill)
                    art.lock(c, nx, ny + 0.6f * u, 4.6f * u, 0xEEFFFFFF.toInt())
                }
            }
        }
        val done = (region.first..region.last).count { progress.hasStar(it) }
        val stars = (region.first..region.last).sumOf { progress.stars(it) }
        val caption = when {
            !active -> "Açmak için ${region.first}. bölümü bitir"
            next in region -> "Sıradaki: ${next + 1} · ${Levels.all[next].name}"
            done == region.count -> "Bölge tamamlandı · ★ $stars/${region.count * 3}"
            else -> "Yıldızlar: $stars/${region.count * 3}"
        }
        art.text(c, caption, 27 * u, yk + 15.5f * u, 3f * u, if (active) 0xDDFFFFFF.toInt() else 0x88FFFFFF.toInt(), android.graphics.Paint.Align.LEFT)
    }

    /** Bölge kartı: altıgen içinde küçük bir manzara (0 orman, 1 göl, 2 mağara, 3 karlı dağ, 4 bulut bahçesi). */
    private fun regionHex(c: Canvas, cx: Float, cy: Float, r: Float, kind: Int, active: Boolean) {
        if (active) art.glow(c, cx, cy, r * 1.6f, 0xFFFFB27A.toInt(), 0.25f)
        c.save()
        c.clipPath(art.hexPath(cx, cy, r))
        if (kind == 0) {
            art.vGradient(c, cx - r, cy - r, cx + r, cy + r, 0xFF6B4C9A.toInt(), 0xFFF4A38E.toInt())
            art.crescent(c, cx + r * 0.35f, cy - r * 0.5f, r * 0.1f, 0xFFFFF1C8.toInt(), 0.3f)
            art.pine(c, cx + r * 0.42f, cy + r * 0.62f, r * 0.55f, r * 1.05f, 0xFFE07A6A.toInt())
            art.pine(c, cx - r * 0.55f, cy + r * 0.7f, r * 0.4f, r * 0.75f, 0xFFB05A7A.toInt())
            art.fill.color = 0xFFF6E3F0.toInt()
            art.rect.set(cx - r, cy + r * 0.45f, cx + r, cy + r * 1.3f)
            c.drawOval(art.rect, art.fill)
            art.lumi(c, cx - r * 0.12f, cy + r * 0.2f + sin(art.time * 2f) * r * 0.04f, r * 0.17f, 0.4f, 0.1f)
        } else if (kind == 1) {
            // göl: ay, ayın sudaki yansıması, nilüferler ve kamışlar
            art.vGradient(c, cx - r, cy - r, cx + r, cy + r, 0xFF3A2F6E.toInt(), 0xFFB98AC8.toInt())
            art.crescent(c, cx - r * 0.3f, cy - r * 0.45f, r * 0.13f, 0xFFFFF1C8.toInt(), 0.35f)
            art.vGradient(c, cx - r, cy + r * 0.15f, cx + r, cy + r, 0xFF4A6FB0.toInt(), 0xFF233E78.toInt())
            art.stroke.strokeWidth = r * 0.04f
            for (i in 0 until 4) {
                art.stroke.color = art.withAlpha(0xFFFFF1C8.toInt(), 0.5f - i * 0.1f)
                val ly = cy + r * (0.3f + i * 0.12f)
                c.drawLine(cx - r * (0.42f - i * 0.04f), ly, cx - r * (0.18f + i * 0.04f), ly, art.stroke)
            }
            for (p in floatArrayOf(0.25f, 0.55f, 0.45f, 0.75f)) {
                val px = cx + r * (p - 0.2f)
                val py = cy + r * (0.35f + (p * 3 % 1f) * 0.3f)
                art.fill.color = 0xFF3FA36B.toInt()
                art.rect.set(px - r * 0.13f, py - r * 0.05f, px + r * 0.13f, py + r * 0.05f)
                c.drawOval(art.rect, art.fill)
                art.flower(c, px, py - r * 0.05f, r * 0.04f, 0xFFFF7EB6.toInt(), 0xFFFFF1A6.toInt())
            }
            art.stroke.color = 0xFF2C3A5A.toInt()
            art.stroke.strokeWidth = r * 0.04f
            for (i in 0 until 3) {
                val rx = cx + r * (0.45f + i * 0.1f)
                c.drawLine(rx, cy + r * 0.3f, rx - r * 0.04f, cy - r * (0.2f + i * 0.1f), art.stroke)
            }
            art.lumi(c, cx + r * 0.05f, cy + sin(art.time * 2f) * r * 0.04f, r * 0.15f, -0.4f, 0.2f)
        } else if (kind == 3) {
            // karlı dağ: gece mavisi, karlı zirveler ve yağan kar
            art.vGradient(c, cx - r, cy - r, cx + r, cy + r, 0xFF1B2A4A.toInt(), 0xFF6F8FBF.toInt())
            for ((mx, mh) in listOf(-0.45f to 0.9f, 0.3f to 1.2f, 0.85f to 0.8f)) {
                val px = cx + r * mx
                val base = cy + r * 0.7f
                art.path.reset()
                art.path.moveTo(px - r * mh * 0.7f, base)
                art.path.lineTo(px, base - r * mh)
                art.path.lineTo(px + r * mh * 0.7f, base)
                art.path.close()
                art.fill.color = 0xFF2C3E66.toInt()
                c.drawPath(art.path, art.fill)
                art.path.reset()
                art.path.moveTo(px - r * mh * 0.2f, base - r * mh * 0.72f)
                art.path.lineTo(px, base - r * mh)
                art.path.lineTo(px + r * mh * 0.2f, base - r * mh * 0.72f)
                art.path.close()
                art.fill.color = 0xFFF0F6FF.toInt()
                c.drawPath(art.path, art.fill)
            }
            art.fill.color = 0xFFE8F0FF.toInt()
            c.drawRect(cx - r, cy + r * 0.68f, cx + r, cy + r, art.fill)
            for (s in 0 until 8) {
                val sx = cx - r + art.frac(s * 0.618f) * 2 * r
                val sy = cy - r + art.frac(s * 0.37f + art.time * 0.08f) * 2 * r
                art.fill.color = 0xCCFFFFFF.toInt()
                c.drawCircle(sx, sy, r * 0.035f, art.fill)
            }
            art.lumi(c, cx - r * 0.1f, cy + r * 0.3f + sin(art.time * 2f) * r * 0.04f, r * 0.15f, 0.3f, 0.1f)
        } else if (kind == 4) {
            // bulut bahçesi: gün batımı göğü, bulutlar ve uçan ada
            art.vGradient(c, cx - r, cy - r, cx + r, cy + r, 0xFF4A3A8A.toInt(), 0xFFF7A8B8.toInt())
            art.fill.color = 0xDDFFFFFF.toInt()
            for ((bx, by) in listOf(-0.5f to 0.55f, 0.1f to 0.62f, 0.6f to 0.5f, -0.2f to -0.35f)) {
                c.drawCircle(cx + r * bx, cy + r * by, r * 0.22f, art.fill)
                c.drawCircle(cx + r * (bx + 0.2f), cy + r * (by + 0.05f), r * 0.17f, art.fill)
            }
            val iy = cy + r * 0.05f + sin(art.time * 1.2f) * r * 0.03f
            art.fill.color = 0xFF5A4370.toInt()
            art.path.reset()
            art.path.moveTo(cx + r * 0.05f, iy)
            art.path.lineTo(cx + r * 0.55f, iy)
            art.path.lineTo(cx + r * 0.3f, iy + r * 0.3f)
            art.path.close()
            c.drawPath(art.path, art.fill)
            art.fill.color = 0xFF6BCB77.toInt()
            art.rect.set(cx + r * 0.02f, iy - r * 0.07f, cx + r * 0.58f, iy + r * 0.03f)
            c.drawRoundRect(art.rect, r * 0.04f, r * 0.04f, art.fill)
            art.lumi(c, cx - r * 0.3f, cy - r * 0.05f + sin(art.time * 2f) * r * 0.04f, r * 0.15f, 0.4f, 0.1f)
        } else {
            // mağara: koyu kaya, sarkıtlar ve parlayan kristaller
            art.vGradient(c, cx - r, cy - r, cx + r, cy + r, 0xFF1A1030.toInt(), 0xFF3C2560.toInt())
            art.fill.color = 0xFF120A22.toInt()
            for (i in 0 until 6) {
                val sx = cx - r + i * r * 0.4f
                art.path.reset()
                art.path.moveTo(sx - r * 0.18f, cy - r)
                art.path.lineTo(sx, cy - r * (0.5f + (i % 3) * 0.12f))
                art.path.lineTo(sx + r * 0.18f, cy - r)
                art.path.close()
                c.drawPath(art.path, art.fill)
            }
            c.drawRect(cx - r, cy + r * 0.55f, cx + r, cy + r, art.fill)
            val crystals = floatArrayOf(-0.5f, 0.32f, -0.15f, 0.42f, 0.45f, 0.36f)
            val crystalColors = intArrayOf(0xFF5BD1FF.toInt(), 0xFFC77DFF.toInt(), 0xFF7CF2B0.toInt())
            for (i in crystalColors.indices) {
                val kx = cx + r * crystals[i * 2]
                val kh = r * crystals[i * 2 + 1]
                val col = crystalColors[i]
                art.glow(c, kx, cy + r * 0.45f, kh * 1.3f, col, 0.45f)
                art.path.reset()
                art.path.moveTo(kx - kh * 0.25f, cy + r * 0.6f)
                art.path.lineTo(kx, cy + r * 0.6f - kh)
                art.path.lineTo(kx + kh * 0.25f, cy + r * 0.6f)
                art.path.close()
                art.fill.color = col
                c.drawPath(art.path, art.fill)
                art.fill.color = 0xFF120A22.toInt()
            }
            art.lumi(c, cx + r * 0.12f, cy - r * 0.05f + sin(art.time * 2f) * r * 0.04f, r * 0.15f, 0.3f, 0.3f)
        }
        if (!active) {
            art.fill.color = 0x77000000
            c.drawRect(cx - r, cy - r, cx + r, cy + r, art.fill)
        }
        c.restore()
        art.stroke.color = if (active) 0xFFFFE9C7.toInt() else 0x55FFFFFF
        art.stroke.strokeWidth = 0.6f * u
        c.drawPath(art.hexPath(cx, cy, r), art.stroke)
    }

    // ---------------------------------------------------------------- ekran 2: oyun alanı

    private fun layoutGrid(g: Game) {
        val top = safeTop + 28 * u
        val bottom = 18 * u
        val availH = height - top - bottom
        val availW = width - 8 * u
        cell = min(availW / g.cols, availH / g.rows)
        gridX = (width - cell * g.cols) / 2
        gridY = top + (availH - cell * g.rows) / 2
    }

    private fun waveProgress(g: Game): Float =
        if (showComplete) 99f else if (g.state == GameState.WON) g.stateTime else 0f

    /** Bitişteki renk dalgasının bu noktaya ulaşma oranı (0..1). */
    private fun waveAt(g: Game, x: Float, y: Float): Float {
        val wave = waveProgress(g) * 8f
        if (wave <= 0f) return 0f
        val dw = hypot(x - (g.exitX + 0.5f), y - (g.exitY + 0.5f))
        return ((wave - dw) / 1.5f).coerceIn(0f, 1f)
    }

    private fun lightAt(g: Game, x: Float, y: Float): Float {
        val d = hypot(x - (g.lumi.px + 0.5f), y - (g.lumi.py + 0.5f))
        return (1f - d / g.lightRadius()).coerceIn(0f, 1f)
    }

    private fun drawGame(c: Canvas) {
        val g = game ?: return
        val def = g.def
        layoutGrid(g)
        val w = width.toFloat()
        val h = height.toFloat()
        val won = g.state == GameState.WON || showComplete
        val skyT = if (won) (waveProgress(g) / 1.4f).coerceIn(0f, 1f) else 0f

        val theme = Levels.themeOf(g.index)
        val boardW = cell * g.cols
        val boardH = cell * g.rows
        drawThemeBackground(c, g, theme, skyT, boardH)

        val board = RectF(gridX, gridY, gridX + boardW, gridY + boardH)
        art.fill.color = 0x40000000
        art.rect.set(board.left - 1.2f * u, board.top - 0.4f * u, board.right + 1.2f * u, board.bottom + 2 * u)
        c.drawRoundRect(art.rect, 2 * u, 2 * u, art.fill)

        // ortama göre zemin: orman çimeni, göl iskelesi tahtası, mağara taşı
        val night: Int
        val nightLit: Int
        val restoredFloor: Int
        when (theme) {
            Theme.FOREST -> {
                night = 0xFF2B2542.toInt(); nightLit = 0xFF463D68.toInt()
                restoredFloor = art.mix(def.leaf, 0xFFE8F0D8.toInt(), 0.25f)
            }
            Theme.LAKE -> {
                night = 0xFF36304A.toInt(); nightLit = 0xFF5C5272.toInt()
                restoredFloor = art.mix(0xFFC9A27A.toInt(), def.leaf, 0.12f)
            }
            Theme.CAVE -> {
                night = 0xFF221D2C.toInt(); nightLit = 0xFF3E3650.toInt()
                restoredFloor = art.mix(def.leaf, 0xFF4A4458.toInt(), 0.5f)
            }
            Theme.SNOW -> {
                night = 0xFF3A4462.toInt(); nightLit = 0xFF66749A.toInt()
                restoredFloor = art.mix(0xFFEFF5FF.toInt(), def.leaf, 0.12f)
            }
            Theme.CLOUD -> {
                night = 0xFF48426E.toInt(); nightLit = 0xFF726A9E.toInt()
                restoredFloor = art.mix(0xFFFFFBF5.toInt(), def.accent, 0.15f)
            }
        }
        val trail = 0xFFFFCB45.toInt()
        val restoredTrail = art.mix(def.accent, 0xFFFFF4E0.toInt(), 0.3f)
        val lavender = 0xFF6F67A8.toInt()
        val outline = 0xFF151230.toInt()

        // zemin kareleri (aralıksız ızgara)
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            val tile = g.tiles[y][x]
            val x0 = gridX + x * cell
            val y0 = gridY + y * cell
            val wv = waveAt(g, x + 0.5f, y + 0.5f)
            val lt = lightAt(g, x + 0.5f, y + 0.5f)
            val hsh = abs((x * 73856093) xor (y * 19349663) xor (g.index * 83492791))
            // görünmez köprü: Pıtır gösterene kadar su ya da boşluk, sonra parlayan tahtalar
            if (tile == Tile.BRIDGE) {
                if (theme == Theme.CLOUD) drawSkyGap(c, x0, y0, hsh) else drawWater(c, x0, y0, lt, wv, hsh)
                drawBridge(c, x0, y0, g.revealed, g.stage >= 1)
                continue
            }
            if (tile == Tile.WATER && theme == Theme.CLOUD) {
                drawSkyGap(c, x0, y0, hsh)
                continue
            }
            if (tile == Tile.WATER || tile == Tile.LILY || (tile == Tile.TREE && theme == Theme.LAKE)) {
                drawWater(c, x0, y0, lt, wv, hsh)
                if (tile == Tile.LILY) drawLily(c, g, x, y, x0, y0, lt, wv)
                continue
            }
            if (tile == Tile.TREE) continue
            if (tile == Tile.ICE) {
                drawIce(c, x0, y0, lt, wv, def.accent, g.isLit(x, y))
                continue
            }
            var col = art.mix(art.mix(night, nightLit, lt * 0.6f), restoredFloor, wv)
            if (g.isLit(x, y)) col = art.mix(trail, restoredTrail, wv)
            art.fill.color = col
            c.drawRect(x0, y0, x0 + cell + 0.5f, y0 + cell + 0.5f, art.fill)
            drawFloorDetail(c, theme, x0, y0, hsh, g.isLit(x, y))
            if (tile == Tile.WIND) {
                val (wdx, wdy) = g.windDir(x, y)
                drawWindArrows(c, x0, y0, wdx, wdy, wv, def.accent)
            }
            art.stroke.color = 0x18FFFFFF
            art.stroke.strokeWidth = max(1f, u * 0.12f)
            c.drawRect(x0, y0, x0 + cell, y0 + cell, art.stroke)
            if (tile == Tile.FLOOR && wv > 0f && !g.isLit(x, y)) {
                if (hsh % 3 == 0) {
                    art.flower(
                        c, x0 + cell * (0.28f + (hsh % 5) * 0.1f), y0 + cell * (0.3f + (hsh / 5 % 5) * 0.1f),
                        cell * 0.09f * wv, def.accent, 0xFFFFF1A6.toInt(), hsh.toFloat(),
                    )
                }
            }
        }

        // ağaçlar, dikenler, kapılar
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            val x0 = gridX + x * cell
            val y0 = gridY + y * cell
            val wv = waveAt(g, x + 0.5f, y + 0.5f)
            when (g.tiles[y][x]) {
                Tile.TREE -> {
                    val lt = lightAt(g, x + 0.5f, y + 0.5f)
                    val leaf = art.mix(art.mix(art.mix(lavender, Color.BLACK, 0.35f), lavender, lt), def.leaf, wv)
                    when (theme) {
                        Theme.FOREST -> art.smallTree(c, x0 + cell / 2, y0 + cell * 0.95f, cell, leaf, outline, x * 7 + y)
                        Theme.LAKE -> drawReeds(c, x0, y0, leaf, x * 7 + y)
                        Theme.CAVE -> drawBoulder(c, x0, y0, lt, wv, def.accent, x * 7 + y)
                        Theme.SNOW -> drawSnowPine(c, x0, y0, leaf, x * 7 + y)
                        Theme.CLOUD -> {
                            val puff = art.mix(art.mix(0xFF5C5690.toInt(), 0xFFB8B0E0.toInt(), lt), Color.WHITE, wv)
                            art.canopy(c, x0 + cell / 2, y0 + cell * 0.55f, cell * 0.36f, puff, 0xFF2A2450.toInt(), 0, x * 7 + y)
                        }
                    }
                }
                Tile.THORN -> drawThorns(c, x0, y0, x + y)
                Tile.DOOR -> drawDoor(c, x0, y0, g.doorsOpen)
                Tile.GATE -> drawGate(c, x0, y0, g.bloomed)
                Tile.BLOOM -> drawBloom(c, x0, y0, g.bloomed)
                else -> Unit
            }
        }

        // karanlık
        val lcx = gridX + (g.lumi.px + 0.5f) * cell
        val lcy = gridY + (g.lumi.py + 0.5f) * cell
        val darkA = if (won) (1f - waveProgress(g) / 1.2f).coerceIn(0f, 1f) else 1f
        if (darkA > 0f) {
            val lr = (g.lightRadius() + sin(art.time * 4f) * 0.06f) * cell
            val edge = 0xFF06050F.toInt()
            art.shader.shader = RadialGradient(
                lcx, lcy, lr,
                intArrayOf(0, art.withAlpha(edge, 0.22f * darkA), art.withAlpha(edge, 0.62f * darkA)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(board, art.shader)
            art.shader.shader = null

            // ışık izi karanlıkta da parlar
            for (y in 0 until g.rows) for (x in 0 until g.cols) {
                if (!g.isLit(x, y) || g.tiles[y][x] == Tile.LILY) continue
                val x0 = gridX + x * cell
                val y0 = gridY + y * cell
                art.fill.color = art.withAlpha(trail, 0.7f * darkA)
                c.drawRect(x0, y0, x0 + cell + 0.5f, y0 + cell + 0.5f, art.fill)
            }
        }

        // dar ışıklı göl bölümlerinde tahtanın üstünde süzülen sis
        if (theme == Theme.LAKE && def.light < 2.2f && darkA > 0f) {
            for (k in 0 until 6) {
                val fx = gridX + art.frac(k * 0.37f + art.time * 0.02f * (1 + k % 2)) * (boardW + 8 * cell) - 4 * cell
                val fy = gridY + boardH * (0.1f + k * 0.16f)
                art.glow(c, fx, fy, cell * (2.2f + k % 3 * 0.5f), 0xFFBFEFEF.toInt(), 0.14f * darkA)
            }
        }

        // parlak nesneler
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            val cx = gridX + (x + 0.5f) * cell
            val cy = gridY + (y + 0.5f) * cell
            when (g.tiles[y][x]) {
                Tile.THORN -> if (darkA > 0f) {
                    art.glow(c, cx, cy + cell * 0.15f, cell * 0.42f, 0xFFB04CE8.toInt(), (0.22f + 0.1f * sin(art.time * 3f + x + y)) * darkA)
                }
                Tile.CRYSTAL -> drawCrystal(c, cx, cy, g.doorsOpen)
                Tile.EXIT -> {
                    val grow = if (won) 1f + min(1f, waveProgress(g)) * 0.35f else 1f
                    val power = if (g.energyFull) 1f else 0.15f + 0.1f * g.collected
                    val leaf = if (won) def.accent else 0xFFFFD25A.toInt()
                    if (!g.energyFull) {
                        art.stroke.color = art.withAlpha(Color.WHITE, 0.18f + 0.15f * sin(art.time * 2f))
                        art.stroke.strokeWidth = cell * 0.025f
                        c.drawCircle(cx, cy, cell * 0.42f, art.stroke)
                    }
                    art.sapling(c, cx, cy + cell * 0.42f, cell * 0.95f * grow, leaf, power)
                }
                else -> Unit
            }
        }
        for ((sx, sy) in g.shards) drawShard(c, gridX + (sx + 0.5f) * cell, gridY + (sy + 0.5f) * cell, sx + sy)
        // gizli parça: arada bir belli belirsiz kıpırdayan bir pırıltı, Pıtır gösterene kadar.
        // Kristal Sezgisi (Orta): yerini kesik çizgili bir elmas olarak hep gösterir.
        if (g.ability == Ability.REVEAL && !g.revealed) {
            for (y in 0 until g.rows) for (x in 0 until g.cols) {
                if (!g.hiddenAt(x, y)) continue
                val hx = gridX + (x + 0.5f) * cell
                val hy = gridY + (y + 0.5f) * cell
                val k = art.frac(art.time / 3.5f + x * 0.13f)
                if (k < 0.15f) {
                    val a = sin(k / 0.15f * Math.PI.toFloat()) * 0.35f
                    art.glow(c, hx, hy, cell * 0.35f, 0xFFFFE39A.toInt(), a)
                }
                if (g.stage >= 1) {
                    val p = art.path
                    p.reset()
                    p.moveTo(hx, hy - cell * 0.28f)
                    p.lineTo(hx + cell * 0.17f, hy)
                    p.lineTo(hx, hy + cell * 0.28f)
                    p.lineTo(hx - cell * 0.17f, hy)
                    p.close()
                    art.stroke.color = art.withAlpha(0xFFFFE39A.toInt(), 0.45f + 0.15f * sin(art.time * 3f))
                    art.stroke.strokeWidth = cell * 0.03f
                    art.stroke.pathEffect = DashPathEffect(floatArrayOf(cell * 0.05f, cell * 0.05f), 0f)
                    c.drawPath(p, art.stroke)
                    art.stroke.pathEffect = null
                }
            }
        }
        // Işık Köprüsü: su ya da boşluk üstünde parlayan saydam kareler
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            if (!g.isLightBridge(x, y)) continue
            val x0 = gridX + x * cell
            val y0 = gridY + y * cell
            val fade = if (g.bridgeMoves > 0) 1f else 0.5f + 0.5f * abs(sin(art.time * 8f))
            art.glow(c, x0 + cell / 2, y0 + cell / 2, cell * 0.7f, 0xFF9CEFF4.toInt(), 0.45f * fade)
            art.fill.color = art.withAlpha(0xFFE0FDFF.toInt(), 0.55f * fade)
            art.rect.set(x0 + cell * 0.08f, y0 + cell * 0.14f, x0 + cell * 0.92f, y0 + cell * 0.86f)
            c.drawRoundRect(art.rect, cell * 0.12f, cell * 0.12f, art.fill)
            art.stroke.color = art.withAlpha(0xFF4FD8E0.toInt(), 0.8f * fade)
            art.stroke.strokeWidth = cell * 0.035f
            c.drawRoundRect(art.rect, cell * 0.12f, cell * 0.12f, art.stroke)
        }
        for ((i, s) in g.shadows.withIndex()) drawMonster(c, g, s, i, lcx, lcy)

        val hugging = hasPitir(g) && hugTime > 0f
        if (hasPitir(g) && !hugging) drawPitir(c, g, theme, lcx, lcy)

        // Lumi ve hareket kuyruğu
        val dying = g.state == GameState.DYING
        if (g.lumi.t < 1f && !dying) {
            val fx = gridX + (g.lumi.fromX + 0.5f) * cell
            val fy = gridY + (g.lumi.fromY + 0.5f) * cell
            // kristalin bıraktığı ince ışık izi
            art.stroke.color = 0x669CEFF4
            art.stroke.strokeWidth = cell * 0.16f
            c.drawLine(fx, fy + cell * 0.1f, lcx, lcy + cell * 0.1f, art.stroke)
        }
        val lumiA = if (dying) 1f - g.stateTime / 0.7f else 1f
        val lumiR = cell * 0.29f * (if (dying) 1f + g.stateTime * 0.2f else 1f)
        val bob = if (g.lumi.t >= 1f) sin(art.time * 2.5f) * cell * 0.008f else 0f
        if (hugging) {
            // yakınlık: ikisini saran yumuşak pembe-camgöbeği ışık
            val k = hugK()
            art.glow(c, lcx + face * cell * 0.2f, lcy - cell * 0.2f, cell * 1.1f, 0xFFFFB8E0.toInt(), 0.35f * k)
            art.glow(c, lcx + face * cell * 0.2f, lcy - cell * 0.2f, cell * 0.7f, 0xFF9CEFF4.toInt(), 0.3f * k)
        }
        art.lumiPlay(
            c, lcx, lcy + bob, lumiR, face, lumiA, walking = g.lumi.t < 1f && !dying,
            pose = if (dying) 0 else if (hugging) 2 else pose,
            poseT = if (hugging) 0.5f * hugK() else poseTime / poseLength(pose),
        )
        if (hugging) {
            drawPitir(c, g, theme, lcx, lcy)
            val t = 1f - hugTime / HUG_TIME
            for (k in 0 until 4) {
                art.heart(c, lcx + face * cell * (0.1f + 0.12f * k), lcy - cell * 0.55f, cell * 0.16f, (t * 1.3f - k * 0.15f))
            }
        }
        if (g.shieldTime > 0f) {
            val a = min(1f, g.shieldTime)
            art.glow(c, lcx, lcy, cell * 0.75f, 0xFF9CEFF4.toInt(), 0.35f * a)
            art.stroke.color = art.withAlpha(0xFFC8FBFF.toInt(), 0.9f * a)
            art.stroke.strokeWidth = cell * 0.045f
            c.drawCircle(lcx, lcy, cell * (0.5f + 0.04f * sin(art.time * 10f)), art.stroke)
        }

        if (dying) {
            art.fill.color = art.withAlpha(0xFF3A1458.toInt(), 0.45f * (1f - g.stateTime / 1.1f))
            c.drawRect(0f, 0f, w, h, art.fill)
        }

        if (hasPitir(g) && bubbleTime > 0f && !showComplete) {
            val (px, py) = pitirPos(g)
            art.bubble(c, bubbleText, px, py - cell * 0.42f, 3.1f * u, w, min(1f, bubbleTime * 3f))
        }

        drawHud(c, g)
        if (monsterIntro.isNotEmpty() && introTime > 0f && !showComplete) drawMonsterIntro(c, monsterIntro.first(), w)

        val hintY = gridY + boardH + (h - gridY - boardH) / 2
        val hint = when {
            dying -> "Işığın söndü... Tekrar dene!"
            won -> def.restoredText
            g.energyFull -> "Enerji doldu! Renk fidanına ulaş."
            else -> def.hint
        }
        val skills = if (hasPitir(g)) skillsFor(g) else emptyList()
        if (skills.isNotEmpty()) {
            val step = 13.5f * u
            val left = w - 4 * u - step * skills.size
            art.wrapped(c, hint, (4 * u + left) / 2, hintY, left - 8 * u, 3.3f * u, 0xE6FFFFFF.toInt())
            for ((k, sk) in skills.withIndex()) drawSkill(c, g, sk, left + step * (k + 0.5f), hintY - 1.2f * u)
        } else {
            art.wrapped(c, hint, w / 2, hintY, 86 * u, 3.5f * u, 0xE6FFFFFF.toInt())
        }

        if (showComplete) drawComplete(c, g)
    }

    // ---------------------------------------------------------------- Pıtır

    /** Pıtır'ın ekrandaki merkezi; Lumi'yle aynı karedeyken (bölüm başı) yanına kayar. */
    private fun pitirPos(g: Game): Pair<Float, Float> {
        var x = g.pitir.px
        var y = g.pitir.py
        if (g.pitir.x == g.lumi.x && g.pitir.y == g.lumi.y && g.pitir.t >= 1f) {
            x -= 0.32f
            y += 0.12f
        }
        return (gridX + (x + 0.5f) * cell) to (gridY + (y + 0.5f) * cell + cell * 0.06f)
    }

    /** Pıtır yetenek düğmesi: bölümde bir kez. Kullanılınca soluklaşır. */
    /** Bir yetenek düğmesi: [icon] 0 Pıtır, 1 köprü, 2 ruhsal geçiş. */
    private class Skill(val id: String, val label: String, val ready: Boolean, val icon: Int, val use: () -> Boolean)

    /**
     * Bölümde kullanılabilen düğmeli yetenekler. Bölümün gerektirdiği yetenek (Renk Görüşü ya da Fener) her aşamada
     * vardır; Işık Köprüsü Orta, Ruhsal Geçiş İleri aşamada açılır.
     */
    private fun skillsFor(g: Game): List<Skill> {
        val live = g.state == GameState.PLAYING && !showComplete
        val list = ArrayList<Skill>()
        when (g.ability) {
            Ability.REVEAL -> list.add(Skill("reveal", "GÖSTER", live && !g.abilityUsed, 0) { g.useAbility() })
            Ability.LANTERN -> list.add(
                Skill("lantern", if (g.lanternMoves > 0) "IŞIK ${g.lanternMoves}" else "FENER", live && !g.abilityUsed, 0) { g.useAbility() },
            )
            null -> Unit
        }
        if (g.canBridge) {
            list.add(Skill("bridge", if (g.bridgeMoves > 0) "KÖPRÜ ${g.bridgeMoves}" else "KÖPRÜ", live && !g.bridgeUsed, 1) {
                g.useBridge().also { if (!it) say("Önümde su yok. Suya dön, öyle bas!", 2f) }
            })
        }
        if (g.canSpirit) {
            list.add(Skill("spirit", "GEÇİŞ", live && !g.spiritUsed, 2) {
                g.useSpirit().also { if (!it) say("Yakında kristal, çiçek ya da parça yok.", 2f) }
            })
        }
        return list
    }

    private fun drawSkill(c: Canvas, g: Game, sk: Skill, cx: Float, cy: Float) {
        val rad = 5.4f * u
        val pressed = isPressed(sk.id)
        if (sk.ready) {
            button(sk.id, roundRect(cx, cy, rad)) { sk.use() }
            art.glow(c, cx, cy, rad * 1.8f, 0xFFFFE39A.toInt(), 0.22f + 0.12f * sin(art.time * 3f + cx))
            art.roundButton(c, cx, cy, rad, pressed, 0xFF9CEFF4.toInt(), 0xFF2FB5C9.toInt())
        } else {
            art.roundButton(c, cx, cy, rad, false, 0xFF5A547A.toInt(), 0xFF3E3960.toInt())
        }
        val iy = cy + if (pressed) 0.35f * u else 0f
        val a = if (sk.ready) 1f else 0.5f
        when (sk.icon) {
            0 -> art.pitir(c, cx, iy + rad * 0.2f, 2.1f * u, 1f, pitirTail, pitirStage, if (sk.ready) 1 else 0, 0.3f, false, a)
            1 -> {
                art.stroke.color = art.withAlpha(Color.WHITE, a)
                art.stroke.strokeWidth = 0.6f * u
                art.rect.set(cx - 3.2f * u, iy - 1.6f * u, cx + 3.2f * u, iy + 3.6f * u)
                c.drawArc(art.rect, 200f, 140f, false, art.stroke)
                for (k in -1..1) c.drawLine(cx + k * 1.6f * u, iy - 0.8f * u, cx + k * 1.6f * u, iy + 1.4f * u, art.stroke)
                c.drawLine(cx - 3.4f * u, iy + 1.4f * u, cx + 3.4f * u, iy + 1.4f * u, art.stroke)
            }
            else -> {
                art.stroke.color = art.withAlpha(Color.WHITE, a)
                art.stroke.strokeWidth = 0.55f * u
                art.stroke.pathEffect = DashPathEffect(floatArrayOf(0.9f * u, 0.7f * u), art.time * 4f * u)
                c.drawCircle(cx, iy, 2.8f * u, art.stroke)
                art.stroke.pathEffect = null
                art.fill.color = art.withAlpha(0xFFFFF3B0.toInt(), a)
                c.drawCircle(cx, iy, 1.1f * u, art.fill)
            }
        }
        art.text(c, sk.label, cx, cy + rad + 2.4f * u, 2.1f * u, if (sk.ready) 0xFFFFFFFF.toInt() else 0x88FFFFFF.toInt(), spacing = 0.08f)
    }

    /**
     * Bir canavarı görseliyle ve türüne göre animasyonla çizer: uçanlar kanat çırpar ve süzülür, yengeç
     * yan yan sekerek yürür, golem ağır adımlarla sallanır, solucan kıvrılır, çiçek salınır. Uyuyan avcı nefes
     * alır ve üstünde "z" çıkar; uyandığı an başında "!" belirir.
     */
    private fun drawMonster(c: Canvas, g: Game, s: Shadow, i: Int, lcx: Float, lcy: Float) {
        val cx = gridX + (s.m.px + 0.5f) * cell
        val cy = gridY + (s.m.py + 0.5f) * cell
        val face = if (s.dx != 0) s.dx.toFloat() else if (lcx < cx) -1f else 1f
        val t = art.time + i * 0.7f
        val moving = s.m.t < 1f
        val step = sin(s.m.t * Math.PI.toFloat())
        val asleep = s.hunter && !s.awake
        val breathe = 1f + 0.025f * sin(t * 2.2f)
        var dy = 0f
        var tilt = 0f
        var sx = 1f
        var sy = 1f
        var alpha = 1f
        when (s.kind) {
            Monster.GOLGE -> {
                dy = sin(t * 3f) * cell * 0.05f
                tilt = sin(t * 1.7f) * 4f
                alpha = 0.88f + 0.12f * sin(t * 5f)
            }
            Monster.CICEK -> {
                tilt = sin(t * 2f) * 6f
                sy = 1f + 0.04f * sin(t * 2.6f)
                if (moving) sy *= 1f - 0.08f * step
            }
            Monster.SU -> {
                dy = sin(t * 2.4f) * cell * 0.07f
                sy = 1f + 0.07f * sin(t * 4f)
                sx = 2f - sy
            }
            Monster.YENGEC -> {
                if (moving) {
                    tilt = sin(t * 22f) * 7f
                    dy = -abs(sin(t * 22f)) * cell * 0.05f
                } else {
                    // beklerken kıskaçlarını açıp kapar gibi
                    sx = 1f + 0.05f * abs(sin(t * 9f))
                }
            }
            Monster.GOLEM -> {
                if (asleep) {
                    sy = breathe
                } else if (moving) {
                    dy = -step * cell * 0.07f
                    tilt = (if ((s.m.x + s.m.y) % 2 == 0) 1f else -1f) * 4f * step
                    sy = 1f - 0.05f * (1f - step)
                }
            }
            Monster.ATES -> {
                dy = sin(t * 4f) * cell * 0.05f
                sy = 1f + 0.09f * sin(t * 30f)
                tilt = sin(t * 2.5f) * 5f
            }
            Monster.KUS -> {
                dy = sin(t * 3f) * cell * 0.06f
                sy = 1f + 0.12f * sin(t * 14f)
                tilt = if (moving) face * 6f else sin(t * 1.5f) * 3f
            }
            Monster.SOLUCAN -> {
                if (asleep) {
                    sy = breathe
                } else {
                    sx = 1f + 0.07f * sin(t * 10f)
                    sy = 2f - sx
                    tilt = sin(t * 5f) * 4f
                }
            }
        }
        if (s.kind == Monster.ATES) art.glow(c, cx, cy + dy, cell * (0.75f + 0.1f * sin(t * 6f)), 0xFFFFD25A.toInt(), 0.35f)
        val drawn = art.monster(c, s.kind, cx, cy + dy, cell, face, alpha = alpha * (if (asleep) 0.85f else 1f), tilt = tilt, sx = sx, sy = sy)
        if (!drawn) art.shadowCreature(c, cx, cy, cell * 0.34f, lcx - cx, lcy - cy, i * 7, s.hunter, s.awake)
        if (asleep) {
            art.text(c, "z", cx + cell * 0.3f, cy - cell * 0.45f - (art.time % 1.5f) * cell * 0.15f, cell * 0.22f, 0xCCFFE39A.toInt())
        }
        if (s.alert > 0f) {
            val pop = easeOutBack(min(1f, (1f - s.alert) * 4f))
            art.glow(c, cx, cy - cell * 0.62f, cell * 0.35f, 0xFFFF7A3D.toInt(), 0.6f * s.alert)
            art.text(c, "!", cx, cy - cell * 0.62f, cell * 0.38f * pop, art.withAlpha(0xFFFFD36A.toInt(), min(1f, s.alert * 2f)))
        }
    }

    /** İlk karşılaşmada canavar tanıtım kartı: görseli, adı, özel gücü ve zayıf yönü. */
    private fun drawMonsterIntro(c: Canvas, m: Monster, w: Float) {
        val a = min(1f, introTime * 2f).coerceAtMost(min(1f, (INTRO_TIME - introTime) * 4f))
        val top = gridY + cell * 0.3f
        val card = RectF(5 * u, top, w - 5 * u, top + 26 * u)
        art.fill.color = art.withAlpha(0xF016253D.toInt(), a)
        c.drawRoundRect(card, 4 * u, 4 * u, art.fill)
        art.stroke.color = art.withAlpha(0xFF7FE6F0.toInt(), 0.6f * a)
        art.stroke.strokeWidth = 0.35f * u
        c.drawRoundRect(card, 4 * u, 4 * u, art.stroke)
        art.monster(c, m, card.left + 12 * u, card.centerY() + u, 20 * u, 1f, alpha = a)
        val x0 = card.left + 24 * u
        val maxW = card.right - 3 * u - x0
        art.text(c, "YENİ CANAVAR", x0, top + 4.5f * u, 2.4f * u, art.withAlpha(0xFFFF8FB8.toInt(), a), android.graphics.Paint.Align.LEFT, spacing = 0.16f)
        art.text(c, m.title, x0, top + 9 * u, 4.2f * u, art.withAlpha(Color.WHITE, a), android.graphics.Paint.Align.LEFT)
        art.wrapped(c, m.trait, x0 + maxW / 2, top + 14.5f * u, maxW, 2.8f * u, art.withAlpha(0xFFFFD36A.toInt(), a))
        art.wrapped(c, "İpucu · " + m.tip, x0 + maxW / 2, top + 20.5f * u, maxW, 2.6f * u, art.withAlpha(0xFF9CEFF4.toInt(), a))
    }

    /** Yakınlık ilerlemesi: hızla yaklaşır, bir süre omzunda kalır, sonra yerine döner (0..1). */
    private fun hugK(): Float {
        val t = 1f - hugTime / HUG_TIME
        return (sin(t * Math.PI.toFloat()) * 1.8f).coerceIn(0f, 1f)
    }

    private fun drawPitir(c: Canvas, g: Game, theme: Theme, lcx: Float, lcy: Float) {
        var (px0, py0) = pitirPos(g)

        // yakınlık: Lumi'nin omzuna konar
        if (hugTime > 0f) {
            val k = hugK()
            px0 += (lcx + face * cell * 0.34f - px0) * k
            py0 += (lcy - cell * 0.3f - py0) * k
        }

        // Renk Dokunuşu ya da Ruhsal Geçiş: Pıtır hedefe koşup geri döner
        if (fetchAnim > 0f) {
            val lv = fetchCell
            if (lv != null) {
                val k = sin((1f - fetchAnim / 1.3f).coerceIn(0f, 1f) * Math.PI.toFloat())
                px0 += (gridX + (lv.first + 0.5f) * cell - px0) * k * 0.85f
                py0 += (gridY + (lv.second + 0.5f) * cell - py0) * k * 0.85f
            }
        }

        // yetenek dalgası ve yanan kuyruk feneri
        if (revealTime > 0f) {
            val k = 1f - revealTime / 1.2f
            art.stroke.color = art.withAlpha(0xFFFFE39A.toInt(), 1f - k)
            art.stroke.strokeWidth = cell * 0.08f * (1f - k)
            c.drawCircle(px0, py0, cell * (0.4f + k * 4f), art.stroke)
        }
        if (g.lanternMoves > 0) {
            art.glow(c, px0, py0, cell * (1.3f + 0.1f * sin(art.time * 5f)), 0xFFFFD36A.toInt(), 0.45f)
        }
        val won = g.state == GameState.WON || showComplete

        // ipucu: kesik çizgiyle hedefe uzanan ışık ve hedefte nabız gibi atan halka
        val hc = hintCell
        if (hc != null && hintTime > 0f) {
            val a = min(1f, hintTime)
            val tx = gridX + (hc.first + 0.5f) * cell
            val ty = gridY + (hc.second + 0.5f) * cell
            art.stroke.color = art.withAlpha(0xFFFFE39A.toInt(), 0.9f * a)
            art.stroke.strokeWidth = cell * 0.05f
            art.stroke.pathEffect = DashPathEffect(floatArrayOf(cell * 0.06f, cell * 0.16f), -art.time * cell)
            c.drawLine(px0, py0, tx, ty, art.stroke)
            art.stroke.pathEffect = null
            val pulse = 0.5f + 0.5f * sin(art.time * 7f)
            art.glow(c, tx, ty, cell * (0.6f + 0.2f * pulse), 0xFFFFE39A.toInt(), 0.5f * a)
            art.stroke.strokeWidth = cell * 0.04f
            c.drawCircle(tx, ty, cell * (0.38f + 0.08f * pulse), art.stroke)
        }

        val happy = mood == Mood.HAPPY || won
        val scared = mood == Mood.SCARED
        val hop = if (happy) abs(sin(art.time * 9f)) * cell * 0.12f else 0f
        val shiver = if (scared) sin(art.time * 45f) * cell * 0.02f else 0f
        val px = px0 + shiver
        val py = py0 - hop
        val look = if (abs(lcx - px) > cell * 0.1f) lcx - px else if (g.dirX != 0) g.dirX.toFloat() else 1f
        val ears = when {
            scared -> -0.8f
            happy -> 0.6f
            theme == Theme.CLOUD -> 1f
            theme == Theme.CAVE -> -0.4f
            else -> 0f
        }
        val eyes = when {
            scared -> 2
            happy -> 1
            else -> 0
        }
        art.glow(c, px, py0 + cell * 0.28f, cell * 0.3f, Color.BLACK, 0.25f)
        art.pitir(c, px, py, cell * 0.2f, look, pitirTail, pitirStage, eyes, ears, theme == Theme.SNOW, spin = pitirSpin)

        // Işık Kalkanı hazır: Pıtır'ın yanında küçük bir kalkan; her koruma için bir tane
        for (k in 0 until g.shields) {
            val sx = px + cell * (0.32f + k * 0.16f)
            val sy = py - cell * 0.3f
            val p = art.path
            p.reset()
            p.moveTo(sx, sy - cell * 0.1f)
            p.lineTo(sx + cell * 0.08f, sy - cell * 0.06f)
            p.lineTo(sx + cell * 0.07f, sy + cell * 0.04f)
            p.lineTo(sx, sy + cell * 0.1f)
            p.lineTo(sx - cell * 0.07f, sy + cell * 0.04f)
            p.lineTo(sx - cell * 0.08f, sy - cell * 0.06f)
            p.close()
            art.glow(c, sx, sy, cell * 0.14f, 0xFF9CEFF4.toInt(), 0.5f)
            art.fill.color = 0xFFC8FBFF.toInt()
            c.drawPath(p, art.fill)
            art.stroke.color = 0xFF167A92.toInt()
            art.stroke.strokeWidth = cell * 0.015f
            c.drawPath(p, art.stroke)
        }

        if (petTime > 0f) {
            val k = 1f - petTime
            art.text(c, "♥", px, py - cell * (0.45f + 0.35f * k), cell * 0.3f, art.withAlpha(0xFFFF7A95.toInt(), petTime))
        }
    }

    // ---------------------------------------------------------------- bölge ortamları

    private fun drawThemeBackground(c: Canvas, g: Game, theme: Theme, skyT: Float, boardH: Float) {
        val def = g.def
        val w = width.toFloat()
        val h = height.toFloat()
        val t = art.time
        when (theme) {
            Theme.FOREST -> {
                art.vGradient(c, 0f, 0f, w, h, art.mix(0xFF2A2147.toInt(), def.skyTop, skyT), art.mix(0xFF143243.toInt(), def.skyBottom, skyT))
                art.twinkles(c, w, h, 1f - skyT)
                art.pineBand(c, w, gridY - 1.5f * u, 15 * u, art.withAlpha(0xFF0E0A22.toInt(), 0.45f), 3)
                art.pineBand(c, w, h + 2 * u, 24 * u, art.withAlpha(0xFF071A24.toInt(), 0.45f), 9)
            }
            Theme.LAKE -> {
                art.vGradient(c, 0f, 0f, w, h, art.mix(0xFF1B1F4A.toInt(), def.skyTop, skyT), art.mix(0xFF0D3550.toInt(), def.skyBottom, skyT))
                art.twinkles(c, w, h, 1f - skyT)
                art.crescent(c, w * 0.84f, gridY - 9 * u, 2.8f * u, 0xFFFFF1C8.toInt(), 0.3f)
                // uzak tepeler ve söğüt siluetleri
                art.fill.color = art.withAlpha(0xFF0B1638.toInt(), 0.7f)
                var x = -4 * u
                var i = 0
                while (x < w + 8 * u) {
                    val r = (6 + 4 * art.frac(i * 0.618f)) * u
                    c.drawCircle(x, gridY - 1.5f * u, r, art.fill)
                    x += 9 * u
                    i++
                }
                for (rx in floatArrayOf(0.06f, 0.2f, 0.78f, 0.95f)) reeds(c, w * rx, gridY - 0.5f * u)
                // tahtanın altındaki göl yüzeyi, ay yansıması ve kamışlar
                val wt = gridY + boardH + u
                art.vGradient(c, 0f, wt, w, h, art.mix(0xFF16305A.toInt(), def.skyBottom, skyT * 0.5f), 0xFF0A1530.toInt())
                art.stroke.strokeWidth = 0.45f * u
                for (k in 0 until 7) {
                    val ly = wt + (2 + k * 2.6f) * u
                    if (ly > h) break
                    val half = (5 - k * 0.5f) * u * (0.8f + 0.2f * sin(t * 1.5f + k))
                    art.stroke.color = art.withAlpha(0xFFFFF1C8.toInt(), 0.35f - k * 0.04f)
                    c.drawLine(w * 0.84f - half, ly, w * 0.84f + half, ly, art.stroke)
                }
                for (rx in floatArrayOf(0.1f, 0.55f, 0.92f)) reeds(c, w * rx, h - 2 * u)
                for (k in 0 until 4) {
                    val fx = w * (0.5f + 0.45f * sin(t * 0.3f + k * 2.1f))
                    val fy = wt + (h - wt) * (0.3f + 0.25f * sin(t * 0.4f + k))
                    art.firefly(c, fx, fy, 0.35f * u, 0.8f)
                }
            }
            Theme.CAVE -> {
                art.vGradient(c, 0f, 0f, w, h, art.mix(0xFF120A20.toInt(), def.skyTop, skyT * 0.8f), art.mix(0xFF23123A.toInt(), def.skyBottom, skyT * 0.6f))
                // tavan ve sarkıtlar
                val ceil = gridY - 14 * u
                art.fill.color = 0xFF0A0614.toInt()
                c.drawRect(0f, 0f, w, ceil, art.fill)
                var x = -u
                var i = 0
                while (x < w + 2 * u) {
                    val len = (5 + 8 * art.frac(i * 0.618f + 0.2f)) * u
                    val hw = (1.6f + 1.4f * art.frac(i * 0.37f)) * u
                    art.path.reset()
                    art.path.moveTo(x - hw, ceil - u)
                    art.path.lineTo(x, ceil + len)
                    art.path.lineTo(x + hw, ceil - u)
                    art.path.close()
                    c.drawPath(art.path, art.fill)
                    if (i % 4 == 1) {
                        val drop = art.frac(t * 0.35f + i * 0.21f)
                        art.fill.color = art.withAlpha(0xFF8EDCFF.toInt(), 0.6f * (1 - drop))
                        c.drawCircle(x, ceil + len + drop * 10 * u, 0.35f * u, art.fill)
                        art.fill.color = 0xFF0A0614.toInt()
                    }
                    x += hw * 2 + 0.6f * u
                    i++
                }
                // dikitler ve parlayan kristal öbekleri
                val floorY = h + u
                art.fill.color = 0xFF0C0718.toInt()
                x = 0f
                i = 3
                while (x < w + 2 * u) {
                    val len = (6 + 10 * art.frac(i * 0.618f)) * u
                    val hw = (2 + 2 * art.frac(i * 0.29f)) * u
                    art.path.reset()
                    art.path.moveTo(x - hw, floorY)
                    art.path.lineTo(x, floorY - len)
                    art.path.lineTo(x + hw, floorY)
                    art.path.close()
                    c.drawPath(art.path, art.fill)
                    x += hw * 2 + 3 * u
                    i++
                }
                val cols = intArrayOf(0xFF5BD1FF.toInt(), 0xFFC77DFF.toInt(), 0xFF7CF2B0.toInt())
                for ((k, cx) in floatArrayOf(0.12f, 0.5f, 0.86f).withIndex()) {
                    val col = art.mix(cols[k], def.accent, skyT)
                    val baseX = w * cx
                    art.glow(c, baseX, h - 4 * u, 10 * u, col, 0.35f + 0.1f * sin(t * 2f + k))
                    for (j in -1..1) {
                        val kh = (5 + 3 * (1 - abs(j))) * u
                        art.path.reset()
                        art.path.moveTo(baseX + j * 2.2f * u - 1.1f * u, h)
                        art.path.lineTo(baseX + j * 2.2f * u + j * 0.8f * u, h - kh)
                        art.path.lineTo(baseX + j * 2.2f * u + 1.1f * u, h)
                        art.path.close()
                        art.fill.color = col
                        c.drawPath(art.path, art.fill)
                    }
                }
                // havada süzülen toz parçacıkları
                for (k in 0 until 14) {
                    val px = art.frac(k * 0.618f + t * 0.01f * (1 + k % 3)) * w
                    val py = art.frac(k * 0.37f - t * 0.02f) * h
                    art.fill.color = art.withAlpha(0xFFBFA8FF.toInt(), 0.25f + 0.2f * sin(t + k))
                    c.drawCircle(px, py, 0.25f * u, art.fill)
                }
            }
            Theme.SNOW -> {
                art.vGradient(c, 0f, 0f, w, h, art.mix(0xFF101A33.toInt(), def.skyTop, skyT), art.mix(0xFF2C4870.toInt(), def.skyBottom, skyT))
                art.twinkles(c, w, h, 1f - skyT)
                // kuzey ışığı perdesi
                for (k in 0 until 3) {
                    art.path.reset()
                    val by = safeTop + (26 + k * 5) * u
                    art.path.moveTo(0f, by)
                    var x = 0f
                    while (x <= w) {
                        art.path.lineTo(x, by + sin(x / (14 * u) + t * 0.6f + k) * 3 * u)
                        x += 2 * u
                    }
                    art.path.lineTo(w, by + 7 * u)
                    art.path.lineTo(0f, by + 7 * u)
                    art.path.close()
                    art.fill.color = art.withAlpha(if (k == 1) 0xFF7CF2B0.toInt() else 0xFF4DD0E1.toInt(), 0.08f * (1f - skyT * 0.5f))
                    c.drawPath(art.path, art.fill)
                }
                // karlı dağlar
                var x = -6 * u
                var i = 0
                while (x < w + 8 * u) {
                    val ph = (10 + 9 * art.frac(i * 0.618f + 0.1f)) * u
                    val hw = ph * 0.8f
                    val base = gridY - 1.2f * u
                    art.path.reset()
                    art.path.moveTo(x - hw, base)
                    art.path.lineTo(x, base - ph)
                    art.path.lineTo(x + hw, base)
                    art.path.close()
                    art.fill.color = 0xFF1A2846.toInt()
                    c.drawPath(art.path, art.fill)
                    art.path.reset()
                    art.path.moveTo(x - hw * 0.3f, base - ph * 0.7f)
                    art.path.lineTo(x, base - ph)
                    art.path.lineTo(x + hw * 0.3f, base - ph * 0.7f)
                    art.path.lineTo(x + hw * 0.1f, base - ph * 0.64f)
                    art.path.lineTo(x - hw * 0.1f, base - ph * 0.72f)
                    art.path.close()
                    art.fill.color = art.withAlpha(0xFFE8F0FF.toInt(), 0.85f)
                    c.drawPath(art.path, art.fill)
                    x += hw * 1.3f
                    i++
                }
                // alttaki karlı çamlar
                art.fill.color = 0xFF16233E.toInt()
                c.drawRect(0f, h - 6 * u, w, h, art.fill)
                art.pineBand(c, w, h - 5 * u, 20 * u, 0xFF1C3048.toInt(), 5)
                // yağan kar
                for (k in 0 until 40) {
                    val sx = art.frac(k * 0.618f + sin(t * 0.5f + k) * 0.01f) * w
                    val sy = art.frac(k * 0.37f + t * (0.04f + (k % 4) * 0.012f)) * h
                    art.fill.color = art.withAlpha(Color.WHITE, 0.5f + 0.3f * (k % 3) / 2f)
                    c.drawCircle(sx, sy, (0.25f + (k % 3) * 0.12f) * u, art.fill)
                }
            }
            Theme.CLOUD -> {
                art.vGradient(c, 0f, 0f, w, h, art.mix(0xFF231E4E.toInt(), def.skyTop, skyT), art.mix(0xFF6A4A88.toInt(), def.skyBottom, skyT))
                art.twinkles(c, w, h, 1f - skyT)
                // uzaktaki bulut kümeleri, yavaşça kayar
                for (band in 0 until 2) {
                    val by = if (band == 0) gridY - 3 * u else h - 4 * u
                    var x = -10 * u + art.frac(t * 0.01f * (band + 1)) * 12 * u
                    var i = band * 5
                    art.fill.color = art.withAlpha(Color.WHITE, if (band == 0) 0.1f else 0.14f)
                    while (x < w + 12 * u) {
                        val r = (5 + 4 * art.frac(i * 0.618f)) * u
                        c.drawCircle(x, by, r, art.fill)
                        c.drawCircle(x + r * 0.9f, by + r * 0.25f, r * 0.75f, art.fill)
                        x += r * 1.9f
                        i++
                    }
                }
                // süzülen küçük adacıklar
                for (k in 0 until 3) {
                    val ix = w * (0.15f + 0.35f * k) + sin(t * 0.3f + k) * 2 * u
                    val iy = gridY - (12 + 4 * (k % 2)) * u + sin(t * 0.8f + k) * u
                    art.fill.color = 0xFF3A2E5A.toInt()
                    art.path.reset()
                    art.path.moveTo(ix - 4 * u, iy)
                    art.path.lineTo(ix + 4 * u, iy)
                    art.path.lineTo(ix, iy + 4 * u)
                    art.path.close()
                    c.drawPath(art.path, art.fill)
                    art.fill.color = art.mix(0xFF4F7A5A.toInt(), def.leaf, skyT)
                    art.rect.set(ix - 4.4f * u, iy - 1.2f * u, ix + 4.4f * u, iy + 0.6f * u)
                    c.drawRoundRect(art.rect, u, u, art.fill)
                }
            }
        }
    }

    /** Buz: yarı saydam mavi, çapraz parıltı çizgileri. */
    private fun drawIce(c: Canvas, x0: Float, y0: Float, lt: Float, wv: Float, accent: Int, lit: Boolean) {
        val base = art.mix(art.mix(0xFF2A4E74.toInt(), 0xFF4F8CC0.toInt(), lt), art.mix(0xFFB4E6FF.toInt(), accent, 0.15f), wv)
        art.fill.color = base
        c.drawRect(x0, y0, x0 + cell + 0.5f, y0 + cell + 0.5f, art.fill)
        art.stroke.strokeWidth = cell * 0.035f
        art.stroke.color = art.withAlpha(Color.WHITE, 0.18f + 0.2f * lt + 0.1f * wv)
        c.drawLine(x0 + cell * 0.18f, y0 + cell * 0.55f, x0 + cell * 0.5f, y0 + cell * 0.2f, art.stroke)
        art.stroke.strokeWidth = cell * 0.02f
        c.drawLine(x0 + cell * 0.45f, y0 + cell * 0.8f, x0 + cell * 0.8f, y0 + cell * 0.42f, art.stroke)
        if (lit) {
            art.fill.color = art.withAlpha(0xFFFFCB45.toInt(), 0.35f)
            c.drawRect(x0, y0, x0 + cell + 0.5f, y0 + cell + 0.5f, art.fill)
        }
        art.stroke.color = 0x22FFFFFF
        art.stroke.strokeWidth = max(1f, u * 0.12f)
        c.drawRect(x0, y0, x0 + cell, y0 + cell, art.stroke)
    }

    /** Rüzgâr: itme yönünde akan üç ok. */
    private fun drawWindArrows(c: Canvas, x0: Float, y0: Float, dx: Int, dy: Int, wv: Float, accent: Int) {
        val cx = x0 + cell / 2
        val cy = y0 + cell / 2
        val col = art.mix(0xFFBFE9FF.toInt(), accent, wv)
        art.stroke.strokeWidth = cell * 0.06f
        for (k in 0 until 3) {
            val p = art.frac(art.time * 1.2f + k / 3f)
            val off = (p - 0.5f) * cell * 0.7f
            val ax = cx + dx * off
            val ay = cy + dy * off
            art.stroke.color = art.withAlpha(col, sin(p * Math.PI.toFloat()) * 0.9f)
            val s = cell * 0.13f
            // ok başı: yöne dik iki kol
            c.drawLine(ax - dx * s + dy * s, ay - dy * s - dx * s, ax, ay, art.stroke)
            c.drawLine(ax - dx * s - dy * s, ay - dy * s + dx * s, ax, ay, art.stroke)
        }
    }

    /** Bulut bahçesinde boşluk: aşağıdaki gökyüzü görünür. */
    private fun drawSkyGap(c: Canvas, x0: Float, y0: Float, hsh: Int) {
        art.fill.color = 0x55100C2A
        c.drawRect(x0, y0, x0 + cell + 0.5f, y0 + cell + 0.5f, art.fill)
        art.fill.color = art.withAlpha(Color.WHITE, 0.3f + 0.3f * sin(art.time * 2f + hsh))
        c.drawCircle(x0 + cell * (0.2f + (hsh % 6) * 0.12f), y0 + cell * (0.25f + (hsh / 6 % 5) * 0.12f), cell * 0.02f, art.fill)
        art.fill.color = art.withAlpha(Color.WHITE, 0.06f)
        c.drawCircle(x0 + cell * 0.3f, y0 + cell * 0.8f, cell * 0.22f, art.fill)
        c.drawCircle(x0 + cell * 0.65f, y0 + cell * 0.85f, cell * 0.18f, art.fill)
    }

    /** Karlı dağ duvarı: üstü karlı çam. */
    private fun drawSnowPine(c: Canvas, x0: Float, y0: Float, leaf: Int, seed: Int) {
        val cx = x0 + cell / 2
        val base = y0 + cell * 0.95f
        art.fill.color = 0xFF4A3A35.toInt()
        c.drawRect(cx - cell * 0.05f, base - cell * 0.18f, cx + cell * 0.05f, base, art.fill)
        art.pine(c, cx, base - cell * 0.12f, cell * 0.78f, cell * 0.8f, art.mix(leaf, 0xFF1C3A44.toInt(), 0.35f))
        art.fill.color = art.withAlpha(0xFFF4F8FF.toInt(), 0.92f)
        for (i in 0 until 3) {
            val ty = base - cell * 0.12f - cell * 0.8f * (0.35f + i * 0.32f) - cell * 0.04f
            val hw = cell * 0.78f * (0.5f - i * 0.12f) * 0.45f
            art.path.reset()
            art.path.moveTo(cx - hw, ty + cell * 0.12f)
            art.path.lineTo(cx, ty)
            art.path.lineTo(cx + hw, ty + cell * 0.12f)
            art.path.quadTo(cx, ty + cell * (0.06f + (seed % 3) * 0.01f), cx - hw, ty + cell * 0.12f)
            c.drawPath(art.path, art.fill)
        }
    }

    private fun drawWater(c: Canvas, x0: Float, y0: Float, lt: Float, wv: Float, hsh: Int) {
        val col = art.mix(art.mix(0xFF12223F.toInt(), 0xFF1F3B68.toInt(), lt * 0.8f), 0xFF2E86C8.toInt(), wv)
        art.fill.color = col
        c.drawRect(x0, y0, x0 + cell + 0.5f, y0 + cell + 0.5f, art.fill)
        art.stroke.strokeWidth = cell * 0.025f
        for (k in 0 until 2) {
            val p = art.frac(art.time * 0.12f + (hsh % 97) * 0.013f + k * 0.5f)
            val ly = y0 + cell * (0.2f + 0.6f * p)
            val lx = x0 + cell * (0.2f + 0.35f * art.frac(hsh * 0.001f + k * 0.4f))
            art.stroke.color = art.withAlpha(Color.WHITE, (0.08f + 0.12f * lt + 0.1f * wv) * sin(p * Math.PI.toFloat()))
            c.drawLine(lx, ly, lx + cell * 0.3f, ly, art.stroke)
        }
    }

    private fun drawLily(c: Canvas, g: Game, x: Int, y: Int, x0: Float, y0: Float, lt: Float, wv: Float) {
        val cx = x0 + cell / 2
        val cy = y0 + cell / 2
        val age = g.sinkAge(x, y)
        val scale = if (age < 0f) 1f else (1f - age / 0.45f).coerceAtLeast(0f)
        if (scale > 0f) {
            val r = cell * 0.38f * scale
            val pad = art.mix(art.mix(0xFF244A3A.toInt(), 0xFF3F8F5C.toInt(), lt), art.mix(g.def.leaf, Color.WHITE, 0.1f), wv)
            art.fill.color = art.mix(pad, Color.BLACK, 0.3f)
            art.rect.set(cx - r, cy - r * 0.85f + cell * 0.04f, cx + r, cy + r * 0.85f + cell * 0.04f)
            c.drawArc(art.rect, 20f, 320f, true, art.fill)
            art.fill.color = pad
            art.rect.set(cx - r, cy - r * 0.85f, cx + r, cy + r * 0.85f)
            c.drawArc(art.rect, 20f, 320f, true, art.fill)
            art.stroke.color = art.mix(pad, Color.WHITE, 0.25f)
            art.stroke.strokeWidth = cell * 0.018f
            for (a in intArrayOf(80, 150, 220, 290)) {
                val rad = Math.toRadians(a.toDouble())
                c.drawLine(cx, cy, cx + (kotlin.math.cos(rad) * r * 0.75f).toFloat(), cy + (sin(rad) * r * 0.65f).toFloat(), art.stroke)
            }
            val petal = art.mix(0xFFE7C6DA.toInt(), g.def.accent, wv)
            art.flower(c, cx - r * 0.25f, cy - r * 0.2f, cell * 0.06f * scale, petal, 0xFFFFF1A6.toInt(), art.time * 0.2f)
        }
        if (age >= 0f && age < 1.2f) {
            art.stroke.color = art.withAlpha(Color.WHITE, 0.5f * (1f - age / 1.2f))
            art.stroke.strokeWidth = cell * 0.025f
            c.drawCircle(cx, cy, cell * (0.18f + age * 0.35f), art.stroke)
        }
    }

    private fun drawFloorDetail(c: Canvas, theme: Theme, x0: Float, y0: Float, hsh: Int, lit: Boolean) {
        val a = if (lit) 0.18f else 0.28f
        when (theme) {
            Theme.FOREST -> Unit
            Theme.SNOW -> {
                // kar parıltıları
                art.fill.color = art.withAlpha(Color.WHITE, 0.25f + 0.2f * sin(art.time * 3f + hsh))
                c.drawCircle(x0 + cell * (0.2f + (hsh % 5) * 0.14f), y0 + cell * (0.25f + (hsh / 5 % 4) * 0.15f), cell * 0.025f, art.fill)
                c.drawCircle(x0 + cell * (0.75f - (hsh % 3) * 0.15f), y0 + cell * 0.72f, cell * 0.02f, art.fill)
            }
            Theme.CLOUD -> {
                // bulut platformunun kabarık üst kenarı
                art.fill.color = art.withAlpha(Color.WHITE, if (lit) 0.12f else 0.1f)
                for (k in 0 until 3) c.drawCircle(x0 + cell * (0.2f + k * 0.3f), y0 + cell * 0.18f, cell * 0.17f, art.fill)
            }
            Theme.LAKE -> {
                // iskele tahtaları
                art.stroke.color = art.withAlpha(Color.BLACK, a)
                art.stroke.strokeWidth = max(1f, cell * 0.022f)
                c.drawLine(x0, y0 + cell / 3, x0 + cell, y0 + cell / 3, art.stroke)
                c.drawLine(x0, y0 + cell * 2 / 3, x0 + cell, y0 + cell * 2 / 3, art.stroke)
                val sx = x0 + cell * (0.25f + (hsh % 5) * 0.12f)
                c.drawLine(sx, y0, sx, y0 + cell / 3, art.stroke)
                art.fill.color = art.withAlpha(Color.BLACK, a)
                c.drawCircle(x0 + cell * 0.12f, y0 + cell * 0.5f, cell * 0.025f, art.fill)
                c.drawCircle(x0 + cell * 0.88f, y0 + cell * 0.5f, cell * 0.025f, art.fill)
            }
            Theme.CAVE -> {
                // taş çatlakları ve çakıllar
                art.stroke.color = art.withAlpha(Color.BLACK, a + 0.05f)
                art.stroke.strokeWidth = max(1f, cell * 0.02f)
                if (hsh % 3 == 0) {
                    val sx = x0 + cell * (0.2f + (hsh % 7) * 0.08f)
                    c.drawLine(sx, y0 + cell * 0.15f, sx + cell * 0.12f, y0 + cell * 0.45f, art.stroke)
                    c.drawLine(sx + cell * 0.12f, y0 + cell * 0.45f, sx + cell * 0.05f, y0 + cell * 0.7f, art.stroke)
                }
                art.fill.color = art.withAlpha(Color.WHITE, 0.07f)
                c.drawCircle(x0 + cell * (0.3f + (hsh % 4) * 0.12f), y0 + cell * 0.78f, cell * 0.04f, art.fill)
                c.drawCircle(x0 + cell * (0.7f - (hsh % 3) * 0.1f), y0 + cell * 0.25f, cell * 0.03f, art.fill)
            }
        }
    }

    /** Göl duvarı: suyun içinde kaya ve kamış öbeği. */
    private fun drawReeds(c: Canvas, x0: Float, y0: Float, leaf: Int, seed: Int) {
        val cx = x0 + cell / 2
        art.fill.color = art.mix(0xFF2E2C40.toInt(), leaf, 0.15f)
        art.rect.set(x0 + cell * 0.14f, y0 + cell * 0.62f, x0 + cell * 0.86f, y0 + cell * 0.95f)
        c.drawOval(art.rect, art.fill)
        val sway = sin(art.time * 1.4f + seed) * cell * 0.03f
        art.stroke.strokeWidth = cell * 0.045f
        for (i in -2..2) {
            val bx = cx + i * cell * 0.11f
            val tx = bx + i * cell * 0.04f + sway
            val ty = y0 + cell * (0.12f + abs(i) * 0.08f)
            art.stroke.color = art.mix(leaf, Color.BLACK, 0.15f * abs(i))
            c.drawLine(bx, y0 + cell * 0.75f, tx, ty, art.stroke)
            if (i % 2 == 0) {
                art.fill.color = 0xFF6B4A35.toInt()
                art.rect.set(tx - cell * 0.045f, ty + cell * 0.02f, tx + cell * 0.045f, ty + cell * 0.2f)
                c.drawRoundRect(art.rect, cell * 0.04f, cell * 0.04f, art.fill)
            }
        }
    }

    /** Mağara duvarı: köşeli kaya, bazılarında parlayan kristal. */
    private fun drawBoulder(c: Canvas, x0: Float, y0: Float, lt: Float, wv: Float, accent: Int, seed: Int) {
        val cx = x0 + cell / 2
        val cy = y0 + cell * 0.56f
        val rock = art.mix(art.mix(0xFF2C2638.toInt(), 0xFF51466A.toInt(), lt), art.mix(0xFF6A6280.toInt(), accent, 0.2f), wv)
        art.path.reset()
        for (k in 0 until 7) {
            val a = Math.toRadians(k * 360.0 / 7 - 90)
            val rr = cell * (0.36f + 0.08f * art.frac((seed + k) * 0.618f))
            val px = cx + (kotlin.math.cos(a) * rr).toFloat()
            val py = cy + (sin(a) * rr * 0.85f).toFloat()
            if (k == 0) art.path.moveTo(px, py) else art.path.lineTo(px, py)
        }
        art.path.close()
        art.fill.color = rock
        c.drawPath(art.path, art.fill)
        art.stroke.color = 0xFF0C0816.toInt()
        art.stroke.strokeWidth = cell * 0.035f
        c.drawPath(art.path, art.stroke)
        art.fill.color = art.mix(rock, Color.WHITE, 0.15f)
        c.drawCircle(cx - cell * 0.12f, cy - cell * 0.14f, cell * 0.08f, art.fill)
        if (seed % 3 == 0) {
            val col = art.mix(0xFF5BD1FF.toInt(), accent, wv)
            val kx = cx + cell * 0.14f
            val ky = cy - cell * 0.12f
            art.glow(c, kx, ky - cell * 0.1f, cell * 0.3f, col, 0.35f)
            art.path.reset()
            art.path.moveTo(kx - cell * 0.06f, ky)
            art.path.lineTo(kx + cell * 0.02f, ky - cell * 0.28f)
            art.path.lineTo(kx + cell * 0.08f, ky)
            art.path.close()
            art.fill.color = col
            c.drawPath(art.path, art.fill)
        }
    }

    private fun drawThorns(c: Canvas, x0: Float, y0: Float, seed: Int) {
        val pulse = 0.5f + 0.5f * sin(art.time * 3f + seed)
        art.glow(c, x0 + cell / 2, y0 + cell * 0.6f, cell * 0.5f, 0xFF9B3FD0.toInt(), 0.18f + 0.12f * pulse)
        for (i in 0 until 4) {
            val bx = x0 + cell * (0.18f + 0.21f * i)
            val tipY = y0 + cell * (0.2f + (i % 2) * 0.16f)
            art.fill.color = art.mix(0xFF1F0E2E.toInt(), 0xFF6E2F92.toInt(), 0.3f + 0.3f * pulse)
            art.path.reset()
            art.path.moveTo(bx - cell * 0.12f, y0 + cell * 0.9f)
            art.path.quadTo(bx - cell * 0.02f, y0 + cell * 0.55f, bx + cell * 0.03f, tipY)
            art.path.lineTo(bx + cell * 0.12f, y0 + cell * 0.9f)
            art.path.close()
            c.drawPath(art.path, art.fill)
        }
    }

    /** Görünmez köprü: kapalıyken suyun üstünde soluk bir kesik çizgi, açılınca parlayan tahtalar. */
    private fun drawBridge(c: Canvas, x0: Float, y0: Float, revealed: Boolean, sensed: Boolean) {
        val r = RectF(x0 + cell * 0.06f, y0 + cell * 0.12f, x0 + cell * 0.94f, y0 + cell * 0.88f)
        if (!revealed) {
            val base = if (sensed) 0.45f else 0.12f
            art.stroke.color = art.withAlpha(0xFF9CEFF4.toInt(), base + 0.08f * sin(art.time * 2f + x0))
            art.stroke.strokeWidth = cell * 0.025f
            art.stroke.pathEffect = DashPathEffect(floatArrayOf(cell * 0.08f, cell * 0.1f), 0f)
            c.drawRoundRect(r, cell * 0.1f, cell * 0.1f, art.stroke)
            art.stroke.pathEffect = null
            return
        }
        art.glow(c, r.centerX(), r.centerY(), cell * 0.6f, 0xFF4FD8E0.toInt(), 0.35f)
        art.fill.color = 0xFFB98A5E.toInt()
        c.drawRoundRect(r, cell * 0.08f, cell * 0.08f, art.fill)
        art.stroke.color = 0xFF6E4A30.toInt()
        art.stroke.strokeWidth = cell * 0.03f
        for (i in 1..3) {
            val lx = r.left + r.width() * i / 4f
            c.drawLine(lx, r.top, lx, r.bottom, art.stroke)
        }
        art.stroke.color = 0xAA9CEFF4.toInt()
        c.drawRoundRect(r, cell * 0.08f, cell * 0.08f, art.stroke)
    }

    /** Kök kapı: şalter çekilene kadar iç içe geçmiş kökler, sonra yere çekilmiş kalıntılar. */
    private fun drawGate(c: Canvas, x0: Float, y0: Float, open: Boolean) {
        val root = 0xFF6B4A3A.toInt()
        art.stroke.color = if (open) art.withAlpha(root, 0.35f) else root
        art.stroke.strokeWidth = cell * (if (open) 0.05f else 0.09f)
        val p = art.path
        for (i in 0 until 3) {
            val bx = x0 + cell * (0.22f + i * 0.28f)
            p.reset()
            if (open) {
                p.moveTo(bx - cell * 0.08f, y0 + cell * 0.9f)
                p.quadTo(bx, y0 + cell * 0.8f, bx + cell * 0.1f, y0 + cell * 0.9f)
            } else {
                p.moveTo(bx, y0 + cell * 0.95f)
                p.cubicTo(bx - cell * 0.12f, y0 + cell * 0.65f, bx + cell * 0.12f, y0 + cell * 0.35f, bx, y0 + cell * 0.05f)
            }
            c.drawPath(p, art.stroke)
        }
        if (!open) {
            art.stroke.strokeWidth = cell * 0.06f
            c.drawLine(x0 + cell * 0.1f, y0 + cell * 0.4f, x0 + cell * 0.9f, y0 + cell * 0.55f, art.stroke)
            art.glow(c, x0 + cell * 0.5f, y0 + cell * 0.5f, cell * 0.3f, 0xFF6BCB77.toInt(), 0.2f)
        }
    }

    /**
     * Solmuş çiçek (Renk Dokunuşu): taş bir çanakta boynu bükük, gri bir çiçek. Lumi giremez; Lumi yanına gelince
     * Pıtır renk verir, çiçek açar ve kök kapılar çekilir.
     */
    private fun drawBloom(c: Canvas, x0: Float, y0: Float, bloomed: Boolean) {
        val cx = x0 + cell / 2
        art.fill.color = 0xFF3A3156.toInt()
        art.rect.set(x0 + cell * 0.18f, y0 + cell * 0.7f, x0 + cell * 0.82f, y0 + cell * 0.92f)
        c.drawRoundRect(art.rect, cell * 0.1f, cell * 0.1f, art.fill)
        val headX = cx + if (bloomed) 0f else cell * 0.12f
        val headY = y0 + cell * (if (bloomed) 0.32f else 0.45f)
        art.stroke.color = if (bloomed) 0xFF4CAF50.toInt() else 0xFF6E6A7E.toInt()
        art.stroke.strokeWidth = cell * 0.05f
        val p = art.path
        p.reset()
        p.moveTo(cx, y0 + cell * 0.72f)
        p.quadTo(cx, headY, headX, headY)
        c.drawPath(p, art.stroke)
        if (bloomed) {
            art.glow(c, headX, headY, cell * 0.5f, 0xFFFF8FB8.toInt(), 0.55f + 0.1f * sin(art.time * 3f))
            art.flower(c, headX, headY, cell * 0.2f, 0xFFFF8FB8.toInt(), 0xFFFFE39A.toInt(), art.time * 20f)
        } else {
            art.glow(c, headX, headY, cell * 0.35f, 0xFFB8B0E0.toInt(), 0.12f + 0.08f * sin(art.time * 2f))
            art.flower(c, headX, headY + cell * 0.04f, cell * 0.14f, 0xFF8A8598.toInt(), 0xFF5E5A6E.toInt(), 30f)
        }
    }

    private fun drawDoor(c: Canvas, x0: Float, y0: Float, open: Boolean) {
        val r = RectF(x0 + cell * 0.08f, y0 + cell * 0.06f, x0 + cell * 0.92f, y0 + cell * 0.94f)
        if (open) {
            art.stroke.strokeWidth = cell * 0.04f
            art.stroke.color = 0x664DC3FF
            c.drawRoundRect(r, cell * 0.1f, cell * 0.1f, art.stroke)
            return
        }
        art.fill.color = 0xFF7A4E33.toInt()
        c.drawRoundRect(r, cell * 0.1f, cell * 0.1f, art.fill)
        art.stroke.strokeWidth = cell * 0.035f
        art.stroke.color = 0xFF4A2D1F.toInt()
        c.drawRoundRect(r, cell * 0.1f, cell * 0.1f, art.stroke)
        for (i in 1..2) {
            val lx = r.left + r.width() * i / 3f
            c.drawLine(lx, r.top + cell * 0.06f, lx, r.bottom - cell * 0.06f, art.stroke)
        }
        art.glow(c, r.centerX(), r.centerY(), cell * 0.3f, 0xFF4DC3FF.toInt(), 0.5f)
        art.fill.color = 0xFF8EDCFF.toInt()
        c.drawCircle(r.centerX(), r.centerY(), cell * 0.09f, art.fill)
    }

    private fun drawCrystal(c: Canvas, cx: Float, cy: Float, active: Boolean) {
        val pulse = 0.5f + 0.5f * sin(art.time * 2.5f)
        art.glow(c, cx, cy, cell * (if (active) 0.95f else 0.65f), 0xFF4DC3FF.toInt(), if (active) 0.8f else 0.35f + 0.25f * pulse)
        val p = art.path
        p.reset()
        p.moveTo(cx, cy - cell * 0.36f)
        p.lineTo(cx + cell * 0.2f, cy - cell * 0.1f)
        p.lineTo(cx + cell * 0.12f, cy + cell * 0.32f)
        p.lineTo(cx - cell * 0.12f, cy + cell * 0.32f)
        p.lineTo(cx - cell * 0.2f, cy - cell * 0.1f)
        p.close()
        art.shader.shader = android.graphics.LinearGradient(
            cx - cell * 0.2f, cy - cell * 0.36f, cx + cell * 0.2f, cy + cell * 0.32f,
            0xFFE3F7FF.toInt(), if (active) 0xFF2E9BFF.toInt() else 0xFF2A6FA8.toInt(), Shader.TileMode.CLAMP,
        )
        c.drawPath(p, art.shader)
        art.shader.shader = null
        art.stroke.strokeWidth = cell * 0.025f
        art.stroke.color = 0x99FFFFFF.toInt()
        c.drawLine(cx, cy - cell * 0.36f, cx, cy + cell * 0.32f, art.stroke)
    }

    private fun drawShard(c: Canvas, cx: Float, baseCy: Float, seed: Int) {
        val cy = baseCy + sin(art.time * 3f + seed) * cell * 0.06f
        art.glow(c, cx, cy, cell * 0.6f, 0xFFFFE9A8.toInt(), 0.75f)
        val half = cell * 0.17f * max(0.3f, abs(kotlin.math.cos(art.time * 2.2f + seed)))
        val p = art.path
        p.reset()
        p.moveTo(cx, cy - cell * 0.24f)
        p.lineTo(cx + half, cy)
        p.lineTo(cx, cy + cell * 0.24f)
        p.lineTo(cx - half, cy)
        p.close()
        art.shader.shader = android.graphics.LinearGradient(
            cx, cy - cell * 0.24f, cx, cy + cell * 0.24f, Color.WHITE, 0xFFFFC94A.toInt(), Shader.TileMode.CLAMP,
        )
        c.drawPath(p, art.shader)
        art.shader.shader = null
    }

    /** Lumi'nin portresindeki ifade: oyundaki duruma göre. */
    private fun hudMood(g: Game): String = when {
        g.state == GameState.DYING -> "uzgun"
        g.state == GameState.WON || showComplete -> "mutlu"
        g.shadows.any { it.alert > 0f } || mood == Mood.SCARED -> "saskin"
        g.energyFull -> "kararli"
        mood == Mood.HAPPY || pose == 2 -> "mutlu"
        else -> "normal"
    }

    /**
     * Üst panel: solda geri düğmesi ve Lumi'nin portresi, ortada bölüm adı, ışık çubuğu ve adım/yıldız,
     * sağda yeniden başlat. Hepsi gece mavisi cam bir şeridin içinde.
     */
    private fun drawHud(c: Canvas, g: Game) {
        val w = width.toFloat()
        val top = safeTop + 2.5f * u
        val panelR = RectF(3 * u, top, w - 3 * u, top + 18 * u)
        art.glass(c, panelR, 5 * u)
        val cy = panelR.centerY()
        if (!showComplete) {
            button("back", roundRect(9.5f * u, cy, 5.5f * u)) { goMenu() }
            button("restart", roundRect(w - 9.5f * u, cy, 5.5f * u)) { g.restart(); energyShown = 0f }
        }
        art.ghostButton(c, 9.5f * u, cy, 4.8f * u, isPressed("back"))
        art.iconBack(c, 9.5f * u, cy, 5.2f * u, Color.WHITE)
        art.ghostButton(c, w - 9.5f * u, cy, 4.8f * u, isPressed("restart"))
        art.iconRestart(c, w - 9.5f * u, cy, 5.2f * u, Color.WHITE)

        art.portrait(c, 22.5f * u, cy, 6.3f * u, hudMood(g))
        val x0 = 31 * u
        val x1 = w - 17 * u
        art.text(c, "Bölüm ${g.index + 1}", x0, top + 4.2f * u, 2.6f * u, 0xFF7FE6F0.toInt(), android.graphics.Paint.Align.LEFT, spacing = 0.1f)
        art.title(c, g.def.name, x0, top + 8.4f * u, 3.9f * u, Color.WHITE, android.graphics.Paint.Align.LEFT)

        val track = RectF(x0, top + 11.6f * u, x1, top + 13.8f * u)
        art.fill.color = 0xCC050A18.toInt()
        c.drawRoundRect(track, 1.1f * u, 1.1f * u, art.fill)
        val f = (energyShown / g.totalShards).coerceIn(0f, 1f)
        if (f > 0.01f) {
            val fr = RectF(track.left, track.top, track.left + track.width() * f, track.bottom)
            art.glow(c, fr.right, track.centerY(), 4 * u, 0xFFFFD45C.toInt(), 0.5f)
            art.shader.shader = android.graphics.LinearGradient(fr.left, 0f, fr.right, 0f, 0xFFFFE08A.toInt(), 0xFFFF8A3D.toInt(), Shader.TileMode.CLAMP)
            c.drawRoundRect(fr, 1.1f * u, 1.1f * u, art.shader)
            art.shader.shader = null
        }
        art.stroke.color = 0x55FFFFFF
        art.stroke.strokeWidth = 0.25f * u
        for (i in 1 until g.totalShards) {
            val tx = track.left + track.width() * i / g.totalShards
            c.drawLine(tx, track.top + 0.3f * u, tx, track.bottom - 0.3f * u, art.stroke)
        }
        art.text(c, "IŞIK ${g.collected}/${g.totalShards} · ADIM ${g.moves}", x0, top + 16.2f * u, 2.4f * u, 0xBBDFFBFF.toInt(), android.graphics.Paint.Align.LEFT, spacing = 0.08f)
        val now = g.starsNow
        for (k in 0 until 3) art.smallStar(c, x1 - (2 - k) * 3.2f * u - 1.2f * u, top + 16.2f * u, 1.25f * u, k < now)
    }

    // ---------------------------------------------------------------- ekran 3: bölüm tamamlandı

    private fun easeOutBack(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val c1 = 1.70158f
        val c3 = c1 + 1
        return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
    }

    /**
     * Bölüm sonu kartı: sırayla beliren yıldızlar, "Bölüm Tamamlandı!", bölgenin sahnesi, yapılanların listesi,
     * ödüller (yıldız, bağ puanı) ve düğmeler: Devam Et (sonraki bölüm), Harita, Tekrar.
     */
    private fun drawComplete(c: Canvas, g: Game) {
        val w = width.toFloat()
        val h = height.toFloat()
        val a = (completeTime / 0.35f).coerceIn(0f, 1f)
        art.fill.color = art.withAlpha(0xFF050A18.toInt(), 0.62f * a)
        c.drawRect(0f, 0f, w, h, art.fill)

        val last = g.index == Levels.all.size - 1
        val regionIdx = Levels.regionOf(g.index)
        val newRegion = if (!last && g.index == Levels.regions[regionIdx].last) Levels.regions.getOrNull(regionIdx + 1) else null
        val cw = 86 * u
        val ch = 140 * u
        val left = (w - cw) / 2
        val top = (h - ch) / 2 + (1 - a) * 14 * u
        val card = RectF(left, top, left + cw, top + ch)
        art.glass(c, card, 6 * u, alpha = a, glowing = true)
        if (a < 1f) return

        // üç yıldız sırayla belirir; kazanılanlar altın, kazanılmayanlar soluk
        val sy = top + 10 * u
        for (k in 0 until 3) {
            val start = 0.3f + 0.32f * k
            val sc = easeOutBack((completeTime - start) / 0.45f)
            if (sc <= 0f) continue
            val earned = k < earnedStars
            if (earned && starSounds <= k) {
                starSounds = k + 1
                sfx.play("shard")
            }
            val sx = w / 2 + (k - 1) * 12 * u
            val r = (if (k == 1) 6.6f else 5f) * u * sc
            val yy = sy + if (k == 1) 0f else 2.2f * u
            if (earned) art.glow(c, sx, yy, r * 2.1f, 0xFFFFD36B.toInt(), 0.45f)
            art.chunkyStar(c, sx, yy, r, earned)
        }
        art.title(c, "Bölüm Tamamlandı!", w / 2, top + 23 * u, 6 * u, 0xFFDFFBFF.toInt())

        val scene = art.scenes.getOrNull(regionIdx.coerceAtMost(art.scenes.size - 1))
        val ir = RectF(left + 4 * u, top + 29 * u, left + cw - 4 * u, top + 61 * u)
        if (scene != null) {
            art.image(c, scene, ir, 3.5f * u)
            art.stroke.color = 0x997FE6F0.toInt()
            art.stroke.strokeWidth = 0.3f * u
            c.drawRoundRect(ir, 3.5f * u, 3.5f * u, art.stroke)
        }

        val verdict = when (earnedStars) {
            3 -> "Mükemmel!"
            2 -> "Çok iyi!"
            else -> "Tamamlandı"
        }
        val lines = ArrayList<Pair<String, Boolean>>()
        lines.add("Bütün ışık parçalarını topladın" to true)
        lines.add(g.def.restoredText to true)
        if (g.def.map.any { it.contains('?') }) lines.add("Saklı parçayı buldun" to true)
        lines.add((if (newRecord) "$verdict · Yeni rekor!" else verdict) + "  ·  ${g.moves} adım" to (earnedStars == 3))
        if (earnedStars < 3) lines.add("3 yıldız için en fazla ${g.def.threeStarMoves} adım" to false)
        var ly = top + 66.5f * u
        for ((text, ok) in lines) {
            val cx = left + 7 * u
            art.fill.color = if (ok) 0xFF2FB5C9.toInt() else 0x553A4A66
            c.drawCircle(cx, ly, 1.8f * u, art.fill)
            if (ok) {
                art.stroke.color = Color.WHITE
                art.stroke.strokeWidth = 0.45f * u
                c.drawLine(cx - 0.8f * u, ly, cx - 0.1f * u, ly + 0.7f * u, art.stroke)
                c.drawLine(cx - 0.1f * u, ly + 0.7f * u, cx + 1f * u, ly - 0.8f * u, art.stroke)
            }
            art.text(c, text, cx + 3.6f * u, ly, 2.9f * u, if (ok) 0xFFE8F6FF.toInt() else 0xAAB8E8F5.toInt(), android.graphics.Paint.Align.LEFT)
            ly += 5.4f * u
        }

        // ödüller
        val ry = top + 96 * u
        art.text(c, "Ödüller", left + 5 * u, ry, 3 * u, 0xFF7FE6F0.toInt(), android.graphics.Paint.Align.LEFT, spacing = 0.08f)
        val rewards = ArrayList<Pair<String, Int>>()
        rewards.add("★ x$earnedStars" to 0xFFFFD36A.toInt())
        if (lastBondGain > 0) rewards.add("♥ +$lastBondGain bağ" to 0xFFFF8FB8.toInt())
        if (last) rewards.add("Işığın Evi" to 0xFF9CEFF4.toInt())
        else if (newRegion != null) rewards.add(newRegion.name.lowercase(trLocale).replaceFirstChar { it.titlecase(trLocale) } to 0xFF9CEFF4.toInt())
        val chipW = (cw - 8 * u - (rewards.size - 1) * 2 * u) / rewards.size
        for ((k, rw) in rewards.withIndex()) {
            val x0 = left + 4 * u + k * (chipW + 2 * u)
            val chip = RectF(x0, ry + 2.5f * u, x0 + chipW, ry + 11.5f * u)
            art.glass(c, chip, 2.5f * u)
            art.text(c, rw.first, chip.centerX(), chip.centerY(), 3.1f * u, rw.second)
        }

        val next = RectF(left + 6 * u, top + 113 * u, left + cw - 6 * u, top + 124 * u)
        button("next", next) { if (last) goMenu() else startLevel(g.index + 1) }
        art.glowButton(c, next, if (last) "Ana Menü" else "Devam Et", true, isPressed("next"))
        val half = (cw - 14 * u) / 2
        val map = RectF(left + 6 * u, top + 127 * u, left + 6 * u + half, top + 135 * u)
        val again = RectF(map.right + 2 * u, map.top, map.right + 2 * u + half, map.bottom)
        button("map", map) { openSelect() }
        button("again", again) { startLevel(g.index) }
        art.glowButton(c, map, "Harita", false, isPressed("map"))
        art.glowButton(c, again, "Tekrar", false, isPressed("again"))
    }

    companion object {
        /** Pıtır 3. bölümde tanışır. */
        const val PITIR_FROM = 2

        /** Yakınlık anının süresi ve oyuncu bu kadar durunca Pıtır'ın kendiliğinden sokulması (saniye). */
        const val HUG_TIME = 1.8f
        const val INTRO_TIME = 5f
        const val IDLE_HUG = 7f

        /** Bu kadar saniye hamle yapılmazsa Pıtır kendiliğinden ipucu verir. */
        const val IDLE_HINT = 15f

        /** Pıtır'ın kuyruk boncukları: bitirilen her bölge bir renk. */
        val TAIL_COLORS = intArrayOf(
            0xFF6BCB77.toInt(), 0xFFFF8FB8.toInt(), 0xFF4FD8E0.toInt(), 0xFF9B6BFF.toInt(), 0xFFFFB86B.toInt(),
        )

        /** Yoldaş sayfasındaki yetenekler: ad, kısa açıklama, açıldığı aşama, simge rengi. */
        private class AbilityInfo(val name: String, val desc: String, val stage: Int, val color: Int)

        private val ABILITIES = listOf(
            AbilityInfo("Renk Görüşü", "Gizli nesneleri ve yolları fark eder", 0, 0xFF2FB5C9.toInt()),
            AbilityInfo("Renk Dokunuşu", "Solmuş çiçekleri açar, kökleri çeker", 0, 0xFF6BCB77.toInt()),
            AbilityInfo("Fener", "Mağarada ışığı büyütür", 0, 0xFFFFC24A.toInt()),
            AbilityInfo("Kristal Sezgisi", "Saklı parçaların yeri hep görünür", 1, 0xFF4FA8E8.toInt()),
            AbilityInfo("Işık Köprüsü", "Suya geçici bir ışık köprüsü kurar", 1, 0xFF9CEFF4.toInt()),
            AbilityInfo("Işık Kalkanı", "Bir darbeye karşı korur", 2, 0xFFB08CFF.toInt()),
            AbilityInfo("Ruhsal Geçiş", "Duvarlardan geçip mekanizma çalıştırır", 2, 0xFFD58CFF.toInt()),
            AbilityInfo("Uyanış", "Bütün yetenekler güçlenir", 3, 0xFFFFD36A.toInt()),
        )

        val STAGE_NAMES = listOf("Başlangıç", "Orta aşama", "İleri aşama", "Ustalık")

        /** Her aşamada açılan yetenekler (TASARIM.md 3.4). */
        val ABILITY_STAGES = listOf(
            listOf("Renk Görüşü", "Renk Dokunuşu", "Fener"),
            listOf("Kristal Sezgisi", "Işık Köprüsü"),
            listOf("Işık Kalkanı", "Ruhsal Geçiş"),
            listOf("Uyanış"),
        )

        val ENCOURAGE = listOf("Az kaldı, bir daha deneyelim!", "Sorun değil, ben buradayım!", "Bu sefer olacak!")
    }
}
