package com.lumi.kayiprenkler

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Bütün çizim parçaları: renk yardımcıları, ışık, Lumi, gölgeler, orman öğeleri ve arayüz.
 * Görseller dosyadan değil, koddan çizilir; böylece her öğe renk geri geldikçe boyanabilir.
 */
class Art {

    var time = 0f
    var u = 1f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val shader = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val path = Path()
    private val path2 = Path()
    val rect = RectF()

    // ---------------------------------------------------------------- karakter görselleri

    /**
     * Lumi ve Pıtır'ın çizilmiş görselleri (res/drawable-nodpi). Yüklenmezse karakterler koddan çizilir.
     * Lumi: önden, yandan (sola bakar) ve arkadan. Pıtır: dört gelişim aşaması.
     */
    private var lumiFront: Bitmap? = null
    private var lumiSide: Bitmap? = null
    private var lumiBack: Bitmap? = null
    private var pitirStages: List<Bitmap> = emptyList()

    /** Oyun içi küçük (chibi) Lumi pozları; hepsi sağa bakar. */
    private var lumiIdle: Bitmap? = null
    private var lumiRun: Bitmap? = null
    private var lumiJump: Bitmap? = null
    private var lumiInteract: Bitmap? = null

    /** Arayüz görselleri: menü sahnesi, Lumi'nin yüz ifadeleri, bölge sahneleri, Pıtır'ın tanıtım görseli. */
    var hero: Bitmap? = null
        private set
    var faces: Map<String, Bitmap> = emptyMap()
        private set
    var scenes: List<Bitmap> = emptyList()
        private set
    var pitirHero: Bitmap? = null
        private set

    /** Canavar görselleri (canavar sayfasından kesildi). */
    var monsters: Map<Monster, Bitmap> = emptyMap()
        private set

    /** Lumi'nin detay görselleri: saç aksesuarı, kolye, çanta, pelerin. */
    var details: List<Bitmap> = emptyList()
        private set
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spriteRect = RectF()

    fun loadSprites(res: Resources) {
        fun load(id: Int): Bitmap? = BitmapFactory.decodeResource(res, id)
        lumiFront = load(R.drawable.lumi_front)
        lumiSide = load(R.drawable.lumi_side)
        lumiBack = load(R.drawable.lumi_back)
        lumiIdle = load(R.drawable.lumi_idle)
        lumiRun = load(R.drawable.lumi_run)
        lumiJump = load(R.drawable.lumi_jump)
        lumiInteract = load(R.drawable.lumi_interact)
        hero = load(R.drawable.ui_hero)
        pitirHero = load(R.drawable.ui_pitir_hero)
        faces = mapOf(
            "normal" to load(R.drawable.face_normal), "mutlu" to load(R.drawable.face_mutlu),
            "uzgun" to load(R.drawable.face_uzgun), "saskin" to load(R.drawable.face_saskin),
            "kararli" to load(R.drawable.face_kararli),
        ).filterValues { it != null }.mapValues { it.value!! }
        scenes = listOfNotNull(
            load(R.drawable.scene_0), load(R.drawable.scene_1), load(R.drawable.scene_2), load(R.drawable.scene_3),
        )
        monsters = mapOf(
            Monster.KUS to load(R.drawable.mon_kus), Monster.GOLEM to load(R.drawable.mon_golem),
            Monster.GOLGE to load(R.drawable.mon_golge), Monster.SOLUCAN to load(R.drawable.mon_solucan),
            Monster.CICEK to load(R.drawable.mon_cicek), Monster.SU to load(R.drawable.mon_su),
            Monster.ATES to load(R.drawable.mon_ates), Monster.YENGEC to load(R.drawable.mon_yengec),
        ).filterValues { it != null }.mapValues { it.value!! }
        details = listOfNotNull(
            load(R.drawable.detail_clip), load(R.drawable.detail_necklace),
            load(R.drawable.detail_bag), load(R.drawable.detail_cloak),
        )
        pitirStages = listOfNotNull(
            load(R.drawable.pitir_0), load(R.drawable.pitir_1), load(R.drawable.pitir_2), load(R.drawable.pitir_3),
        )
    }

    /** Görseli [r] içine, oranını koruyup ortadan kırparak ve köşeleri yuvarlatarak çizer. */
    fun image(c: Canvas, b: Bitmap, r: RectF, radius: Float) {
        val want = r.width() / r.height()
        val have = b.width.toFloat() / b.height
        val src = if (have > want) {
            val sw = (b.height * want).toInt()
            android.graphics.Rect((b.width - sw) / 2, 0, (b.width + sw) / 2, b.height)
        } else {
            val sh = (b.width / want).toInt()
            android.graphics.Rect(0, (b.height - sh) / 2, b.width, (b.height + sh) / 2)
        }
        c.save()
        path.reset()
        path.addRoundRect(r, radius, radius, Path.Direction.CW)
        c.clipPath(path)
        bitmapPaint.alpha = 255
        c.drawBitmap(b, src, r, bitmapPaint)
        c.restore()
    }

    // ---------------------------------------------------------------- yeni arayüz: gece mavisi cam paneller

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    /** Başlık yazısı (serif, hafif camgöbeği ışıltılı). */
    fun title(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        titlePaint.textSize = size
        titlePaint.textAlign = align
        titlePaint.color = color
        titlePaint.setShadowLayer(size * 0.35f, 0f, 0f, withAlpha(0xFF7FE6F0.toInt(), 0.55f * Color.alpha(color) / 255f))
        c.drawText(s, x, y - (titlePaint.descent() + titlePaint.ascent()) / 2, titlePaint)
    }

    /** Koyu gece mavisi, yarı saydam, camgöbeği kenarlı panel. [glowing] dışına hafif ışık verir. */
    fun glass(c: Canvas, r: RectF, radius: Float, alpha: Float = 1f, glowing: Boolean = false) {
        if (glowing) {
            stroke.color = withAlpha(0xFF7FE6F0.toInt(), 0.18f * alpha)
            stroke.strokeWidth = radius * 0.5f
            c.drawRoundRect(r, radius, radius, stroke)
        }
        shader.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xE6132A4A.toInt(), 0xE60B1A33.toInt(), Shader.TileMode.CLAMP)
        shader.alpha = (255 * alpha).toInt()
        c.drawRoundRect(r, radius, radius, shader)
        shader.shader = null
        shader.alpha = 255
        stroke.color = withAlpha(0xFF7FE6F0.toInt(), 0.45f * alpha)
        stroke.strokeWidth = radius * 0.08f
        c.drawRoundRect(r, radius, radius, stroke)
    }

    /** Işıklı hap düğme. [primary] camgöbeği-mavi dolgulu ve parlak; değilse cam. Solda küçük bir elmas simge. */
    fun glowButton(c: Canvas, r: RectF, label: String, primary: Boolean, pressed: Boolean, alpha: Float = 1f) {
        val rr = RectF(r)
        if (pressed) rr.inset(r.width() * 0.01f, r.height() * 0.04f)
        val rad = rr.height() / 2
        if (primary) {
            glow(c, rr.centerX(), rr.centerY(), rr.width() * 0.6f, 0xFF4FC3F7.toInt(), 0.35f * alpha)
            shader.shader = LinearGradient(rr.left, 0f, rr.right, 0f, 0xFF3FB6E8.toInt(), 0xFF2E6FD0.toInt(), Shader.TileMode.CLAMP)
            shader.alpha = (255 * alpha).toInt()
            c.drawRoundRect(rr, rad, rad, shader)
            shader.shader = null
            shader.alpha = 255
            stroke.color = withAlpha(0xFFC8FBFF.toInt(), 0.9f * alpha)
            stroke.strokeWidth = rad * 0.1f
            c.drawRoundRect(rr, rad, rad, stroke)
            fill.color = withAlpha(Color.WHITE, 0.18f * alpha)
            rect.set(rr.left + rad * 0.6f, rr.top + rad * 0.18f, rr.right - rad * 0.6f, rr.top + rad * 0.75f)
            c.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
        } else {
            glass(c, rr, rad, alpha)
        }
        val ix = rr.left + rad * 1.1f
        val s = rad * 0.32f
        path.reset()
        path.moveTo(ix, rr.centerY() - s)
        path.lineTo(ix + s * 0.7f, rr.centerY())
        path.lineTo(ix, rr.centerY() + s)
        path.lineTo(ix - s * 0.7f, rr.centerY())
        path.close()
        fill.color = withAlpha(if (primary) Color.WHITE else 0xFF7FE6F0.toInt(), alpha)
        c.drawPath(path, fill)
        text(c, label, ix + rad * 0.7f, rr.centerY(), rad * 0.72f, withAlpha(Color.WHITE, alpha), Paint.Align.LEFT)
    }

    /** Lumi'nin yuvarlak portresi: [mood] normal, mutlu, uzgun, saskin, kararli. */
    fun portrait(c: Canvas, cx: Float, cy: Float, r: Float, mood: String) {
        glow(c, cx, cy, r * 1.6f, 0xFF7FE6F0.toInt(), 0.35f)
        val b = faces[mood] ?: faces["normal"]
        fill.color = 0xFF0B1A33.toInt()
        c.drawCircle(cx, cy, r, fill)
        if (b != null) {
            c.save()
            path.reset()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            c.clipPath(path)
            val src = android.graphics.Rect(b.width / 10, 0, b.width - b.width / 10, b.width - b.width / 5)
            rect.set(cx - r, cy - r, cx + r, cy + r)
            bitmapPaint.alpha = 255
            c.drawBitmap(b, src, rect, bitmapPaint)
            c.restore()
        }
        stroke.color = 0xFFC8FBFF.toInt()
        stroke.strokeWidth = r * 0.1f
        c.drawCircle(cx, cy, r, stroke)
    }

    /** Yükselip sönen küçük bir kalp. [t] 0..1. */
    fun heart(c: Canvas, x: Float, y: Float, s: Float, t: Float) {
        if (t <= 0f || t >= 1f) return
        val a = if (t < 0.2f) t / 0.2f else 1f - (t - 0.2f) / 0.8f
        val yy = y - t * s * 3f
        glow(c, x, yy, s * 1.4f, 0xFFFF8FB8.toInt(), 0.5f * a)
        text(c, "♥", x + sin(t * 9f) * s * 0.3f, yy, s * (0.8f + 0.4f * t), withAlpha(0xFFFF7A95.toInt(), a))
    }

    /**
     * Canavarı görseliyle çizer: [cx],[cy] karenin ortası, [cell] kare boyu. [face] sola giderken aynalanır.
     * [scale] büyüme/küçülme, [alpha] saydamlık, [tilt] eğim, [flip] ters dönmüş (devrik yengeç).
     */
    fun monster(
        c: Canvas, kind: Monster, cx: Float, cy: Float, cell: Float, face: Float, scale: Float = 1f,
        alpha: Float = 1f, tilt: Float = 0f, flipped: Boolean = false, sx: Float = 1f, sy: Float = 1f,
    ): Boolean {
        val b = monsters[kind] ?: return false
        val h0 = cell * when (kind) {
            Monster.KUS, Monster.SOLUCAN -> 0.85f
            Monster.CICEK, Monster.GOLEM -> 1.05f
            else -> 0.95f
        }
        // geniş görseller bir buçuk kareyi geçmesin
        val h = min(h0, cell * 1.55f * b.height / b.width) * scale
        val mirror = face < 0f && kind in setOf(Monster.KUS, Monster.SOLUCAN, Monster.ATES, Monster.YENGEC)
        c.save()
        c.translate(cx, cy)
        if (tilt != 0f) c.rotate(tilt)
        if (flipped) c.scale(1f, -1f)
        // esneme ve ezilme: tabanı yerde kalsın diye alt kenara göre
        if (sx != 1f || sy != 1f) c.scale(sx, sy, 0f, h / 2 - cell * 0.05f)
        val w = h * b.width / b.height
        spriteRect.set(-w / 2, -h / 2 - cell * 0.05f, w / 2, h / 2 - cell * 0.05f)
        if (mirror) c.scale(-1f, 1f)
        bitmapPaint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        c.drawBitmap(b, null, spriteRect, bitmapPaint)
        c.restore()
        return true
    }

    /** Görseli tabanı [bottom] üstünde, ortası [cx] olacak şekilde [height] boyunda çizer; [flip] yatay aynalar. */
    private fun sprite(
        c: Canvas, b: Bitmap, cx: Float, bottom: Float, height: Float, flip: Boolean, alpha: Float,
        tilt: Float = 0f, scaleX: Float = 1f, scaleY: Float = 1f,
    ) {
        val w = height * b.width / b.height
        c.save()
        c.translate(cx, bottom)
        if (tilt != 0f) c.rotate(tilt)
        c.scale(if (flip) -scaleX else scaleX, scaleY)
        spriteRect.set(-w / 2, -height, w / 2, 0f)
        bitmapPaint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        c.drawBitmap(b, null, spriteRect, bitmapPaint)
        c.restore()
    }

    // ---------------------------------------------------------------- renk

    fun withAlpha(c: Int, a: Float): Int =
        (c and 0xFFFFFF) or ((Color.alpha(c) * a.coerceIn(0f, 1f)).toInt() shl 24)

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * k).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }

    fun frac(v: Float) = v - floor(v)

    // ---------------------------------------------------------------- yazı

    fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, shadow: Int = 0, spacing: Float = 0f,
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.textAlign = align
        textPaint.letterSpacing = spacing
        if (shadow != 0) textPaint.setShadowLayer(size * 0.14f, 0f, size * 0.07f, shadow)
        else textPaint.clearShadowLayer()
        c.drawText(s, x, y - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
    }

    fun wrapped(c: Canvas, s: String, cx: Float, y: Float, maxW: Float, size: Float, color: Int) {
        textPaint.textSize = size
        textPaint.letterSpacing = 0f
        val lines = ArrayList<String>()
        var line = ""
        for (word in s.split(" ")) {
            val tryLine = if (line.isEmpty()) word else "$line $word"
            if (textPaint.measureText(tryLine) > maxW && line.isNotEmpty()) {
                lines.add(line)
                line = word
            } else line = tryLine
        }
        if (line.isNotEmpty()) lines.add(line)
        val lh = size * 1.35f
        val top = y - lh * (lines.size - 1) / 2
        for ((i, l) in lines.withIndex()) text(c, l, cx, top + i * lh, size, color)
    }

    // ---------------------------------------------------------------- ışık ve zemin

    fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, a: Float) {
        if (a <= 0f || r <= 0f) return
        shader.shader = RadialGradient(
            cx, cy, r, intArrayOf(withAlpha(color, a), withAlpha(color, a * 0.3f), 0),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(cx, cy, r, shader)
        shader.shader = null
    }

    fun vGradient(c: Canvas, l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        shader.shader = LinearGradient(0f, t, 0f, b, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(l, t, r, b, shader)
        shader.shader = null
    }

    fun twinkles(c: Canvas, w: Float, h: Float, alpha: Float) {
        if (alpha <= 0f) return
        for (i in 0 until 34) {
            val sx = frac(i * 0.618034f + 0.11f) * w
            val sy = frac(i * 0.381966f + 0.07f) * h * 0.55f
            val a = 0.25f + 0.75f * abs(sin(time * 1.2f + i * 1.7f))
            fill.color = withAlpha(Color.WHITE, a * 0.75f * alpha)
            c.drawCircle(sx, sy, u * (0.2f + (i % 3) * 0.12f), fill)
        }
    }

    fun crescent(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, glowA: Float) {
        glow(c, cx, cy, r * 4f, color, glowA)
        path.reset()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        path2.reset()
        path2.addCircle(cx + r * 0.42f, cy - r * 0.28f, r * 0.9f, Path.Direction.CW)
        path.op(path2, Path.Op.DIFFERENCE)
        fill.color = color
        c.drawPath(path, fill)
    }

    // ---------------------------------------------------------------- orman parçaları

    private val canopyBlobs = floatArrayOf(
        0f, -0.42f, 0.5f,
        -0.52f, -0.1f, 0.47f,
        0.52f, -0.1f, 0.47f,
        -0.32f, 0.3f, 0.44f,
        0.34f, 0.3f, 0.44f,
        0f, 0.02f, 0.56f,
    )

    /** Çizgi film tarzı, kenarı çizgili, gölgeli ve üstünde çiçekler olan bulut ağaç tacı. */
    fun canopy(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, outline: Int, flowers: Int, seed: Int) {
        stroke.color = outline
        stroke.strokeWidth = r * 0.1f
        for (i in canopyBlobs.indices step 3) {
            c.drawCircle(cx + canopyBlobs[i] * r, cy + canopyBlobs[i + 1] * r, canopyBlobs[i + 2] * r, stroke)
        }
        fill.color = mix(color, Color.BLACK, 0.3f)
        for (i in canopyBlobs.indices step 3) {
            c.drawCircle(cx + canopyBlobs[i] * r, cy + canopyBlobs[i + 1] * r, canopyBlobs[i + 2] * r, fill)
        }
        fill.color = color
        for (i in canopyBlobs.indices step 3) {
            c.drawCircle(
                cx + canopyBlobs[i] * r, cy + canopyBlobs[i + 1] * r - r * 0.08f,
                canopyBlobs[i + 2] * r * 0.88f, fill,
            )
        }
        fill.color = mix(color, Color.WHITE, 0.2f)
        for (i in 0 until 9 step 3) {
            c.drawCircle(
                cx + (canopyBlobs[i] - 0.1f) * r, cy + (canopyBlobs[i + 1] - 0.16f) * r,
                canopyBlobs[i + 2] * r * 0.38f, fill,
            )
        }
        val petal = mix(color, Color.WHITE, 0.5f)
        val center = mix(color, Color.WHITE, 0.8f)
        for (i in 0 until flowers) {
            val a = seed + i * 2.4f
            val rad = 0.18f + frac(i * 0.37f + seed * 0.11f) * 0.55f
            flower(c, cx + cos(a) * rad * r, cy + sin(a) * rad * r * 0.75f - r * 0.06f, r * 0.055f, petal, center, a)
        }
    }

    fun bigTree(c: Canvas, baseX: Float, baseY: Float, h: Float, leaf: Int, bark: Int, outline: Int, lean: Float, seed: Int) {
        val topX = baseX + lean * h * 0.22f
        val topY = baseY - h * 0.6f
        path.reset()
        path.moveTo(baseX - h * 0.1f, baseY)
        path.quadTo(baseX - h * 0.01f + lean * h * 0.12f, baseY - h * 0.3f, topX - h * 0.035f, topY)
        path.lineTo(topX + h * 0.035f, topY)
        path.quadTo(baseX + h * 0.06f + lean * h * 0.14f, baseY - h * 0.28f, baseX + h * 0.1f, baseY)
        path.close()
        fill.color = bark
        c.drawPath(path, fill)
        stroke.color = outline
        stroke.strokeWidth = h * 0.012f
        c.drawPath(path, stroke)
        stroke.color = bark
        stroke.strokeWidth = h * 0.028f
        for (side in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(topX, topY + h * 0.08f)
            path.quadTo(topX + side * h * 0.08f, topY - h * 0.02f, topX + side * h * 0.22f, topY - h * 0.06f)
            c.drawPath(path, stroke)
        }
        canopy(c, topX, topY - h * 0.14f, h * 0.36f, leaf, outline, 9, seed)
    }

    fun smallTree(c: Canvas, cx: Float, baseY: Float, s: Float, leaf: Int, outline: Int, seed: Int) {
        fill.color = mix(0xFF6B5A7A.toInt(), leaf, 0.15f)
        rect.set(cx - s * 0.07f, baseY - s * 0.36f, cx + s * 0.07f, baseY)
        c.drawRoundRect(rect, s * 0.03f, s * 0.03f, fill)
        canopy(c, cx, baseY - s * 0.56f, s * 0.34f, leaf, outline, 3, seed)
    }

    fun pine(c: Canvas, x: Float, baseY: Float, w: Float, h: Float, color: Int) {
        fill.color = color
        for (i in 0 until 3) {
            val ty = baseY - h * (0.35f + i * 0.32f)
            val by = baseY - h * (i * 0.27f)
            val hw = w * (0.5f - i * 0.12f)
            path.reset()
            path.moveTo(x - hw, by)
            path.lineTo(x, ty - h * 0.05f)
            path.lineTo(x + hw, by)
            path.close()
            c.drawPath(path, fill)
        }
    }

    fun pineBand(c: Canvas, w: Float, baseY: Float, maxH: Float, color: Int, seed: Int) {
        var x = -u * 2
        var i = seed
        while (x < w + u * 4) {
            val h = maxH * (0.45f + 0.55f * frac(i * 0.618f + 0.3f))
            pine(c, x, baseY, h * 0.55f, h, color)
            x += h * 0.33f + u * 1.5f
            i++
        }
    }

    fun fern(c: Canvas, x: Float, baseY: Float, h: Float, color: Int, seed: Int) {
        val sway = sin(time * 1.3f + seed) * 0.04f
        fill.color = color
        stroke.color = mix(color, Color.BLACK, 0.25f)
        stroke.strokeWidth = h * 0.025f
        for (i in 0 until 5) {
            val a = Math.toRadians((-58.0 + i * 29.0)).toFloat() + sway
            val tipX = x + sin(a) * h
            val tipY = baseY - cos(a) * h * (if (i == 2) 1f else 0.85f)
            val cX = x + sin(a) * h * 0.25f
            val cY = baseY - h * 0.8f
            path.reset()
            path.moveTo(x, baseY)
            path.quadTo(cX, cY, tipX, tipY)
            c.drawPath(path, stroke)
            for (k in 1..8) {
                val t = k / 9f
                val it = 1 - t
                val px = it * it * x + 2 * it * t * cX + t * t * tipX
                val py = it * it * baseY + 2 * it * t * cY + t * t * tipY
                val tx = 2 * it * (cX - x) + 2 * t * (tipX - cX)
                val ty = 2 * it * (cY - baseY) + 2 * t * (tipY - cY)
                val deg = Math.toDegrees(atan2(ty, tx).toDouble()).toFloat()
                val len = h * 0.15f * (1 - t * 0.6f)
                for (side in intArrayOf(-1, 1)) {
                    c.save()
                    c.translate(px, py)
                    c.rotate(deg + side * 58f)
                    rect.set(0f, -len * 0.22f, len, len * 0.22f)
                    c.drawOval(rect, fill)
                    c.restore()
                }
            }
        }
    }

    fun mushroom(c: Canvas, x: Float, baseY: Float, s: Float, cap: Int, glowA: Float) {
        glow(c, x, baseY - s * 0.55f, s * 1.1f, cap, glowA)
        fill.color = 0xFFEDE3D0.toInt()
        rect.set(x - s * 0.12f, baseY - s * 0.52f, x + s * 0.12f, baseY)
        c.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
        fill.color = cap
        rect.set(x - s * 0.42f, baseY - s * 0.82f, x + s * 0.42f, baseY - s * 0.3f)
        c.drawArc(rect, 180f, 180f, true, fill)
        fill.color = withAlpha(Color.WHITE, 0.85f)
        c.drawCircle(x - s * 0.18f, baseY - s * 0.66f, s * 0.07f, fill)
        c.drawCircle(x + s * 0.12f, baseY - s * 0.72f, s * 0.05f, fill)
        c.drawCircle(x + s * 0.26f, baseY - s * 0.6f, s * 0.045f, fill)
    }

    fun flower(c: Canvas, cx: Float, cy: Float, r: Float, petal: Int, center: Int, rot: Float = 0f) {
        fill.color = petal
        for (i in 0 until 5) {
            val a = rot + i * 2 * PI.toFloat() / 5
            c.drawCircle(cx + cos(a) * r, cy + sin(a) * r, r * 0.75f, fill)
        }
        fill.color = center
        c.drawCircle(cx, cy, r * 0.6f, fill)
    }

    /** Renk fidanı: güç (0..1) arttıkça parlar; yapraklar istenen renge boyanır. */
    fun sapling(c: Canvas, cx: Float, baseY: Float, s: Float, leaf: Int, power: Float) {
        val topY = baseY - s * 0.6f
        if (power > 0f) glow(c, cx, topY, s * (0.9f + 0.1f * sin(time * 3f)), leaf, 0.75f * power)
        val trunk = mix(0xFF6E6A85.toInt(), 0xFFFFD86B.toInt(), power)
        stroke.color = trunk
        stroke.strokeWidth = s * 0.05f
        path.reset()
        path.moveTo(cx, baseY)
        path.quadTo(cx - s * 0.04f, baseY - s * 0.3f, cx, topY)
        c.drawPath(path, stroke)
        stroke.strokeWidth = s * 0.03f
        c.drawLine(cx, baseY - s * 0.35f, cx - s * 0.16f, baseY - s * 0.5f, stroke)
        c.drawLine(cx, baseY - s * 0.42f, cx + s * 0.17f, baseY - s * 0.56f, stroke)
        val leafCol = mix(0xFF7C7896.toInt(), leaf, power)
        fill.color = leafCol
        val leaves = floatArrayOf(-150f, 0.24f, -120f, 0.3f, -90f, 0.34f, -60f, 0.3f, -30f, 0.24f)
        for (i in leaves.indices step 2) {
            val deg = leaves[i]
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val lx = cx + cos(rad) * s * leaves[i + 1] * 0.62f
            val ly = topY + sin(rad) * s * leaves[i + 1] * 0.62f
            c.save()
            c.translate(lx, ly)
            c.rotate(deg + 90f)
            rect.set(-s * 0.07f, -s * 0.2f, s * 0.07f, s * 0.03f)
            c.drawOval(rect, fill)
            c.restore()
        }
        fill.color = mix(leafCol, Color.WHITE, 0.45f * power)
        c.drawCircle(cx, topY - s * 0.02f, s * 0.05f, fill)
        if (power > 0.5f) {
            for (i in 0 until 3) {
                val a = time * 1.6f + i * 2.1f
                val px = cx + cos(a) * s * 0.42f
                val py = topY - s * 0.05f + sin(a * 1.3f) * s * 0.2f
                firefly(c, px, py, s * 0.025f, power)
            }
        }
    }

    fun firefly(c: Canvas, x: Float, y: Float, s: Float, a: Float) {
        glow(c, x, y, s * 6f, 0xFFFFE27A.toInt(), 0.7f * a)
        val flap = 0.35f + 0.65f * abs(sin(time * 22f + x))
        fill.color = withAlpha(Color.WHITE, 0.55f * a)
        rect.set(x - s * 2.2f, y - s * 1.2f * flap, x - s * 0.2f, y + s * 0.2f)
        c.drawOval(rect, fill)
        rect.set(x + s * 0.2f, y - s * 1.2f * flap, x + s * 2.2f, y + s * 0.2f)
        c.drawOval(rect, fill)
        fill.color = withAlpha(0xFFFFF4C4.toInt(), a)
        c.drawCircle(x, y, s, fill)
    }

    // ---------------------------------------------------------------- karakterler

    /**
     * Lumi: dalgalı kahverengi saçlı, saçında altın yaprak tokası olan genç bir kaşif. Beyaz, altın işlemeli,
     * mavi astarlı bir pelerin, koyu bir yelek, boynunda mavi bir enerji kristali, yanında deri bir çanta ve botlar.
     * Işığı boynundaki kristalden yayılır. [r] boyut ölçüsü (boyu yaklaşık 3,4 r).
     * [dx],[dy] baktığı yön: sola bakarken aynalanır, yukarı giderken sırtı görünür. [walking] adım animasyonu.
     */
    /**
     * Oyun içi Lumi: küçük (chibi) pozlarla. [face] -1 sola, 1 sağa. [pose]: 0 yok, 1 zıplama, 2 etkileşim,
     * 3 özel yetenek; [poseT] pozun ilerlemesi (0..1). [walking] iki kareli koşu.
     */
    fun lumiPlay(
        c: Canvas, cx: Float, cy: Float, r: Float, face: Float, alpha: Float, walking: Boolean, pose: Int, poseT: Float,
    ) {
        val idle = lumiIdle
        if (idle == null || alpha <= 0f) {
            lumi(c, cx, cy, r, face, 0f, alpha, walking)
            return
        }
        val flip = face < 0f
        val base = r * 3.6f
        fun h(b: Bitmap) = base * b.height / idle.height
        val t = poseT.coerceIn(0f, 1f)
        val bottom0 = cy + r * 1.75f

        // kristalin ışığı ve gölge (zıplarken gölge küçülür)
        val lift = if (pose == 1) sin(t * PI.toFloat()) * r * 1.3f else if (pose == 3) sin(t * PI.toFloat()) * r * 0.35f else 0f
        glow(c, cx, cy - lift, r * 3.6f, 0xFFFFE7A0.toInt(), 0.5f * alpha)
        fill.color = withAlpha(0x44000000, alpha * (1f - lift / (r * 2.6f)))
        val sw = r * 0.7f * (1f - lift / (r * 3f))
        rect.set(cx - sw, bottom0 - r * 0.12f, cx + sw, bottom0 + r * 0.12f)
        c.drawOval(rect, fill)

        if (pose == 3) {
            // özel yetenek: ayaklarının çevresinde dönen altın ve camgöbeği ışık halkaları
            val k = sin(t * PI.toFloat())
            glow(c, cx, cy - lift, r * 4.5f, 0xFF9CEFF4.toInt(), 0.5f * k * alpha)
            for (ring in 0..1) {
                val col = if (ring == 0) 0xFFFFD36A.toInt() else 0xFF7FE6F0.toInt()
                c.save()
                c.translate(cx, bottom0 - r * (0.4f + ring * 1.2f) - lift * 0.5f)
                c.scale(1f, 0.35f)
                c.rotate(time * (if (ring == 0) 260f else -320f))
                stroke.color = withAlpha(col, 0.9f * k * alpha)
                stroke.strokeWidth = r * 0.22f
                rect.set(-r * 2f, -r * 2f, r * 2f, r * 2f)
                c.drawArc(rect, 0f, 200f, false, stroke)
                stroke.strokeWidth = r * 0.1f
                c.drawArc(rect, 220f, 90f, false, stroke)
                c.restore()
            }
            for (k2 in 0 until 6) {
                val a = (t * 2f + k2 / 6f) % 1f
                val sx = cx + sin(k2 * 2.1f + time * 3f) * r * 1.6f
                val sy = bottom0 - a * r * 4.5f
                glow(c, sx, sy, r * 0.25f, if (k2 % 2 == 0) 0xFFFFE39A.toInt() else 0xFF9CEFF4.toInt(), (1f - a) * k * alpha)
            }
        }

        when {
            pose == 1 -> {
                // zıplama: kalkışta ve inişte ezilir, havada uzar
                val sq = when {
                    t < 0.15f -> 1f - 0.18f * (t / 0.15f)
                    t > 0.85f -> 1f - 0.18f * ((1f - t) / 0.15f)
                    else -> 1.06f
                }
                val b = if (t in 0.12f..0.88f) lumiJump ?: idle else idle
                sprite(c, b, cx, bottom0 - lift, h(b) * sq, flip, alpha, scaleX = 2f - sq)
            }
            pose == 2 -> {
                // etkileşim: öne uzanır, elinden pırıltılar çıkar
                val b = lumiInteract ?: idle
                sprite(c, b, cx, bottom0, h(b), flip, alpha, tilt = (if (flip) -1f else 1f) * 4f * sin(t * PI.toFloat()))
                val hx = cx + (if (flip) -1f else 1f) * r * 0.95f
                val hy = cy + r * 0.1f
                val k = sin(t * PI.toFloat())
                glow(c, hx, hy, r * 1.1f, 0xFF9CEFF4.toInt(), 0.8f * k * alpha)
                for (k2 in 0 until 5) {
                    val ang = k2 * 1.26f + time * 4f
                    val d = r * (0.3f + 0.9f * t)
                    fill.color = withAlpha(Color.WHITE, k * alpha)
                    c.drawCircle(hx + cos(ang) * d, hy + sin(ang) * d, r * 0.07f, fill)
                }
            }
            pose == 3 -> sprite(c, idle, cx, bottom0 - lift, h(idle), flip, alpha)
            walking -> {
                // iki kareli koşu: adım ve duruş sırayla, her adımda hafif sıçrama
                val ph = sin(time * 18f)
                val b = if (ph > 0f) lumiRun ?: idle else idle
                val bob = abs(ph) * r * 0.12f
                sprite(c, b, cx, bottom0 - bob, h(b), flip, alpha)
            }
            else -> {
                // nefes alır gibi hafifçe
                val br = 1f + 0.015f * sin(time * 2.5f)
                sprite(c, idle, cx, bottom0, h(idle) * br, flip, alpha)
            }
        }
    }

    fun lumi(
        c: Canvas, cx: Float, cy: Float, r: Float, dx: Float, dy: Float, alpha: Float = 1f, walking: Boolean = false,
    ) {
        if (alpha <= 0f) return
        val front = lumiFront
        if (front != null) {
            // çizilmiş görsel: yöne göre önden, yandan ya da arkadan; yürürken hafif sallanır
            glow(c, cx, cy, r * 3.6f, 0xFFFFE7A0.toInt(), 0.5f * alpha)
            fill.color = withAlpha(0x44000000, alpha)
            rect.set(cx - r * 0.7f, cy + r * 1.6f, cx + r * 0.7f, cy + r * 1.85f)
            c.drawOval(rect, fill)
            val side = abs(dx) > 0.5f
            val b = when {
                side -> lumiSide ?: front
                dy < -0.5f -> lumiBack ?: front
                else -> front
            }
            val step = if (walking) sin(time * 20f) else 0f
            val bob = if (walking) abs(step) * r * 0.12f else 0f
            sprite(c, b, cx, cy + r * 1.75f - bob, r * 3.9f, flip = side && dx > 0f, alpha = alpha, tilt = step * 3f)
            // boynundaki kristal parlar: oyundaki ışığın kaynağı
            if (b !== lumiBack) glow(c, cx + (if (side) -r * 0.05f * (if (dx > 0f) -1f else 1f) else 0f), cy - r * 0.35f, r * 0.45f, 0xFF9CEFF4.toInt(), 0.7f * alpha)
            return
        }
        val s = r / 10f
        val f = if (dx < -0.05f) -1f else 1f
        val back = dy < -0.5f && abs(dx) < 0.5f
        fun x(v: Float) = cx + v * s * f
        fun y(v: Float) = cy + v * s
        fun a(col: Int) = withAlpha(col, alpha)

        val hair = 0xFF4A2E22.toInt()
        val hairLight = 0xFF6E4636.toInt()
        val skin = 0xFFFBE1D0.toInt()
        val cloak = 0xFFF4EFE6.toInt()
        val lining = 0xFF3E7FB8.toInt()
        val gold = 0xFFE2B85A.toInt()
        val vest = 0xFF2E3A5A.toInt()
        val leather = 0xFF7A4E33.toInt()
        val ink = 0xFF2A1E1A.toInt()

        // kristalin ışığı ve yer gölgesi
        glow(c, x(0f), y(3f), r * 3.6f, 0xFFFFE7A0.toInt(), 0.5f * alpha)
        fill.color = a(0x44000000)
        rect.set(x(-6f), y(16f), x(6f), y(18.5f))
        c.drawOval(rect, fill)

        val step = if (walking) sin(time * 20f) else 0f

        // arkadaki uzun dalgalı saç
        path.reset()
        path.moveTo(x(-7.5f), y(-9f))
        path.cubicTo(x(-11f), y(-2f), x(-9f), y(2f), x(-10.5f), y(6f))
        path.quadTo(x(-8f), y(7.5f), x(-6.5f), y(5.5f))
        path.quadTo(x(-4f), y(7f), x(-2f), y(4f))
        path.lineTo(x(2f), y(4f))
        path.quadTo(x(4f), y(7f), x(6.5f), y(5.5f))
        path.quadTo(x(8f), y(7.5f), x(10.5f), y(6f))
        path.cubicTo(x(9f), y(2f), x(11f), y(-2f), x(7.5f), y(-9f))
        path.close()
        fill.color = a(hair)
        c.drawPath(path, fill)

        // pelerin (arka): omuzlardan açılan beyaz, mavi astarlı, altın kenarlı
        path.reset()
        path.moveTo(x(-4.5f), y(0f))
        path.lineTo(x(-9.5f + step * 0.4f), y(12f))
        path.quadTo(x(0f), y(13.5f), x(9.5f - step * 0.4f), y(12f))
        path.lineTo(x(4.5f), y(0f))
        path.close()
        fill.color = a(if (back) cloak else lining)
        c.drawPath(path, fill)
        stroke.color = a(gold)
        stroke.strokeWidth = 0.8f * s
        path.reset()
        path.moveTo(x(-9.5f + step * 0.4f), y(12f))
        path.quadTo(x(0f), y(13.5f), x(9.5f - step * 0.4f), y(12f))
        c.drawPath(path, stroke)

        // bacaklar ve botlar
        for (side in intArrayOf(-1, 1)) {
            val lift = if (side < 0) step else -step
            val lx = side * 2.2f
            fill.color = a(0xFF252033.toInt())
            rect.set(x(lx - 1.5f), y(8f), x(lx + 1.5f), y(14f - lift * 0.8f))
            c.drawRect(rect, fill)
            fill.color = a(0xFF8A5A3A.toInt())
            rect.set(x(lx - 1.9f), y(12.5f - lift * 0.8f), x(lx + 2.1f), y(16.5f - lift * 0.8f))
            c.drawRoundRect(rect, 1.2f * s, 1.2f * s, fill)
            fill.color = a(0xFF5E3C26.toInt())
            rect.set(x(lx - 1.9f), y(15.6f - lift * 0.8f), x(lx + 2.1f), y(16.6f - lift * 0.8f))
            c.drawRect(rect, fill)
        }

        // gövde: koyu yelek, kemer
        fill.color = a(vest)
        rect.set(x(-4f), y(0.5f), x(4f), y(9f))
        c.drawRoundRect(rect, 1.5f * s, 1.5f * s, fill)
        fill.color = a(leather)
        rect.set(x(-4f), y(6.5f), x(4f), y(7.7f))
        c.drawRect(rect, fill)
        fill.color = a(gold)
        c.drawCircle(x(0f), y(7.1f), 0.6f * s, fill)

        if (!back) {
            // çanta askısı ve çanta
            stroke.color = a(0xFF5E3C26.toInt())
            stroke.strokeWidth = 0.9f * s
            c.drawLine(x(-3.5f), y(0.5f), x(5.5f), y(6f), stroke)
            fill.color = a(leather)
            rect.set(x(4.5f), y(5f), x(9f), y(10f))
            c.drawRoundRect(rect, 1f * s, 1f * s, fill)
            fill.color = a(0xFF5E3C26.toInt())
            rect.set(x(4.5f), y(5f), x(9f), y(7f))
            c.drawRoundRect(rect, 1f * s, 1f * s, fill)
            fill.color = a(gold)
            c.drawCircle(x(6.75f), y(7.2f), 0.5f * s, fill)
        }

        // pelerinin ön kanatları ve kollar
        for (side in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(x(side * 3.5f), y(-0.5f))
            path.lineTo(x(side * 7.5f), y(1.5f))
            path.lineTo(x(side * 8.5f), y(10f))
            path.lineTo(x(side * 4.5f), y(9f))
            path.close()
            fill.color = a(cloak)
            c.drawPath(path, fill)
            stroke.color = a(gold)
            stroke.strokeWidth = 0.6f * s
            c.drawLine(x(side * 8.5f), y(10f), x(side * 4.5f), y(9f), stroke)
            c.drawLine(x(side * 6.5f), y(4f), x(side * 6.8f), y(7f), stroke)
            // eldivenli el
            fill.color = a(0xFF2A2433.toInt())
            c.drawCircle(x(side * 6.8f), y(9.6f + (if (side < 0) -step else step) * 0.4f), 1.2f * s, fill)
        }
        // mavi başlık yakası
        path.reset()
        path.moveTo(x(-5f), y(-0.5f))
        path.quadTo(x(0f), y(3.2f), x(5f), y(-0.5f))
        path.quadTo(x(0f), y(1f), x(-5f), y(-0.5f))
        fill.color = a(lining)
        c.drawPath(path, fill)

        if (!back) {
            // boyundaki enerji kristali
            glow(c, x(0f), y(3.4f), 3.2f * s, 0xFF7FE6F0.toInt(), 0.9f * alpha)
            stroke.color = a(gold)
            stroke.strokeWidth = 0.4f * s
            c.drawLine(x(-2f), y(0.5f), x(0f), y(2f), stroke)
            c.drawLine(x(2f), y(0.5f), x(0f), y(2f), stroke)
            path.reset()
            path.moveTo(x(0f), y(2f))
            path.lineTo(x(1.1f), y(3.4f))
            path.lineTo(x(0f), y(5.2f))
            path.lineTo(x(-1.1f), y(3.4f))
            path.close()
            fill.color = a(0xFF9CEFF4.toInt())
            c.drawPath(path, fill)
            stroke.color = a(gold)
            c.drawPath(path, stroke)
        }

        // baş
        fill.color = a(skin)
        c.drawCircle(x(0f), y(-7f), 7f * s, fill)

        if (back) {
            // sırttan: saç bütün başı kaplar
            fill.color = a(hair)
            c.drawCircle(x(0f), y(-7.5f), 7.8f * s, fill)
            stroke.color = a(hairLight)
            stroke.strokeWidth = 0.6f * s
            for (k in -1..1) {
                path.reset()
                path.moveTo(x(k * 2.5f), y(-14f))
                path.quadTo(x(k * 3.5f + 1f), y(-7f), x(k * 2.8f), y(0f))
                c.drawPath(path, stroke)
            }
        } else {
            // perçem ve yan saçlar
            path.reset()
            path.moveTo(x(-7.6f), y(-4f))
            path.cubicTo(x(-8.5f), y(-13f), x(-4f), y(-15.5f), x(0f), y(-15f))
            path.cubicTo(x(5f), y(-15.5f), x(8.5f), y(-12f), x(7.6f), y(-4f))
            path.lineTo(x(6.5f), y(-2f))
            path.quadTo(x(6.2f), y(-8f), x(4.5f), y(-9.5f))
            path.quadTo(x(3.5f), y(-7.5f), x(2f), y(-8f))
            path.quadTo(x(1f), y(-10f), x(-0.5f), y(-9.5f))
            path.quadTo(x(-2f), y(-7.5f), x(-3.5f), y(-8.5f))
            path.quadTo(x(-5.5f), y(-9f), x(-6.2f), y(-6f))
            path.lineTo(x(-6.5f), y(-2f))
            path.close()
            fill.color = a(hair)
            c.drawPath(path, fill)
            stroke.color = a(hairLight)
            stroke.strokeWidth = 0.5f * s
            path.reset()
            path.moveTo(x(-4f), y(-13.5f))
            path.quadTo(x(-1f), y(-14.5f), x(2f), y(-13.8f))
            c.drawPath(path, stroke)
            // yanlarda omuza dökülen bukleler
            fill.color = a(hair)
            for (side in intArrayOf(-1, 1)) {
                path.reset()
                path.moveTo(x(side * 7.6f), y(-6f))
                path.quadTo(x(side * 8.8f), y(-1f), x(side * 7f), y(2f))
                path.quadTo(x(side * 6f), y(-2f), x(side * 6.2f), y(-5f))
                path.close()
                c.drawPath(path, fill)
            }

            // gözler: iri, kahverengi-yeşil, parlak
            val ex = dx * 0.8f
            val ey = dy * 0.5f
            val blink = (time % 3.7f) < 0.13f
            for (side in intArrayOf(-1, 1)) {
                val px = x(side * 2.7f + ex)
                val py = y(-5.6f + ey)
                if (blink) {
                    stroke.color = a(ink)
                    stroke.strokeWidth = 0.6f * s
                    c.drawLine(px - 1.5f * s, py, px + 1.5f * s, py, stroke)
                    continue
                }
                rect.set(px - 1.5f * s, py - 2f * s, px + 1.5f * s, py + 2f * s)
                shader.shader = LinearGradient(0f, py - 2f * s, 0f, py + 2f * s, 0xFF3B2A1E.toInt(), 0xFF8A7A3A.toInt(), Shader.TileMode.CLAMP)
                shader.alpha = (255 * alpha).toInt()
                c.drawOval(rect, shader)
                shader.shader = null
                shader.alpha = 255
                fill.color = a(Color.WHITE)
                c.drawCircle(px + 0.5f * s, py - 0.8f * s, 0.6f * s, fill)
                c.drawCircle(px - 0.4f * s, py + 0.8f * s, 0.25f * s, fill)
                stroke.color = a(ink)
                stroke.strokeWidth = 0.45f * s
                c.drawLine(px - 1.6f * s, py - 2.1f * s, px + 1.6f * s, py - 2.1f * s, stroke)
            }
            fill.color = a(0x66FF8FA3)
            c.drawCircle(x(-4.3f + ex * 0.5f), y(-3.2f), 1.1f * s, fill)
            c.drawCircle(x(4.3f + ex * 0.5f), y(-3.2f), 1.1f * s, fill)
            stroke.color = a(0xFF8A4A3A.toInt())
            stroke.strokeWidth = 0.45f * s
            rect.set(x(-1f + ex), y(-3.6f + ey), x(1f + ex), y(-2.2f + ey))
            c.drawArc(rect, 20f, 140f, false, stroke)
        }

        // altın yaprak tokası (ucunda renk kristali)
        val clipX = if (back) 4.5f else 4.8f
        for (k in -1..1) {
            c.save()
            c.translate(x(clipX), y(-11.5f))
            c.rotate((k * 40f - 20f) * f)
            rect.set(-0.8f * s, -3.2f * s, 0.8f * s, 0f)
            fill.color = a(gold)
            c.drawOval(rect, fill)
            c.restore()
        }
        fill.color = a(0xFF9CEFF4.toInt())
        c.drawCircle(x(clipX), y(-11.5f), 0.6f * s, fill)
    }

    /**
     * Pıtır: Lumi'nin yoldaşı. Camgöbeği ışıktan, iri kulaklı ve iri gözlü küçük bir ruh; başında bir filiz var.
     * [r] boyut ölçüsü (başın yarıçapına yakın). [tail] her bölge için bir kuyruk boncuğu rengi (0 = henüz yok).
     * [stage] bağla gelişen görünüm: 0 Başlangıç, 1 Orta (kristal kanatlar), 2 İleri (pembe-mor kanatlar ve hale),
     * 3 Ustalık (gökkuşağı kanatlar, taç ve altın hale).
     * [eyes]: 0 açık, 1 mutlu (kapalı kemer), 2 kocaman (korku). [ears] 0 normal, 1 iyice açık, -1 düşük.
     * [face] -1 sola, 1 sağa bakar; kuyruk ters tarafta durur.
     */
    fun pitir(
        c: Canvas, cx: Float, cy: Float, r: Float, face: Float, tail: IntArray, stage: Int,
        eyes: Int, ears: Float, scarf: Boolean, alpha: Float = 1f, spin: Float = 0f,
    ) {
        if (alpha <= 0f) return
        val img = pitirStages.getOrNull(stage.coerceIn(0, 3))
        if (img != null) {
            // çizilmiş görsel: aşamaya göre; ruh hâli boyutla ve eğimle verilir
            val aura = when (stage) {
                0 -> 0xFF7FE6F0.toInt()
                1 -> 0xFFB8FFF5.toInt()
                2 -> 0xFFD58CFF.toInt()
                else -> 0xFFFFD36A.toInt()
            }
            glow(c, cx, cy - r * 0.6f, r * (2.0f + stage * 0.5f), aura, (0.3f + stage * 0.1f) * alpha)
            val h = r * (2.7f + stage * 0.15f)
            val squash = when (eyes) {
                1 -> 1f + 0.05f * sin(time * 18f)
                2 -> 0.92f
                else -> 1f + 0.02f * sin(time * 3f)
            }
            // özel yetenekte bir tur döner ve ışık saçar
            if (spin > 0f) glow(c, cx, cy - r * 0.6f, r * 4f, aura, 0.6f * sin(spin * PI.toFloat()) * alpha)
            val turn = if (spin > 0f) cos(spin * 2f * PI.toFloat()) else 1f
            val rise = if (spin > 0f) sin(spin * PI.toFloat()) * r * 0.8f else 0f
            sprite(
                c, img, cx, cy + r * 1.7f - rise, h * squash, flip = face < 0f, alpha = alpha, tilt = ears * -4f,
                scaleX = if (abs(turn) < 0.08f) 0.08f else turn,
            )
            if (stage >= 2) {
                val n = if (stage == 2) 3 else 5
                for (k in 0 until n) {
                    val ang = time * 1.4f + k * (2f * PI.toFloat() / n)
                    val sx = cx + cos(ang) * r * 1.9f
                    val sy = cy - r * 0.4f + sin(ang) * r * 1.2f
                    val tw = 0.5f + 0.5f * sin(time * 5f + k)
                    glow(c, sx, sy, r * 0.18f, aura, 0.8f * tw * alpha)
                    fill.color = withAlpha(Color.WHITE, alpha)
                    c.drawCircle(sx, sy, r * 0.05f * (0.6f + tw), fill)
                }
            }
            return
        }
        val s = r / 22f
        val f = if (face < 0f) -1f else 1f
        fun px(x: Float) = cx + (x - 50f) * s * f
        fun py(y: Float) = cy + (y - 55f) * s
        fun a(col: Int) = withAlpha(col, alpha)

        val light = 0xFFC8FBFF.toInt()
        val mid = 0xFF7FE6F0.toInt()
        val deep = 0xFF2FB5C9.toInt()
        val line = 0xFF167A92.toInt()
        val innerEar = when (stage) {
            0 -> 0xFFB8FFF0.toInt()
            1 -> 0xFFFFF3B0.toInt()
            2 -> 0xFFFFB8E8.toInt()
            else -> 0xFFFFD98A.toInt()
        }
        val aura = when (stage) {
            0 -> 0xFF7FE6F0.toInt()
            1 -> 0xFFB8FFF5.toInt()
            2 -> 0xFFD58CFF.toInt()
            else -> 0xFFFFD36A.toInt()
        }

        // hale
        glow(c, cx, py(50f), r * (2.0f + stage * 0.5f), aura, (0.28f + stage * 0.1f) * alpha)

        // kanatlar (Orta seviyeden itibaren)
        if (stage >= 1) {
            val size = when (stage) { 1 -> 24f; 2 -> 33f; else -> 40f }
            val flap = 0.82f + 0.18f * sin(time * 6f)
            for (side in intArrayOf(-1, 1)) {
                c.save()
                c.translate(px(50f + side * 10f), py(60f))
                c.scale(side * f, flap)
                for (k in 0..1) {
                    // üst ve alt kanat yaprağı
                    val ang = if (k == 0) -38f else 12f
                    val len = size * s * (if (k == 0) 1f else 0.7f)
                    c.save()
                    c.rotate(ang)
                    rect.set(0f, -len * 0.32f, len, len * 0.32f)
                    val cols = when (stage) {
                        1 -> intArrayOf(0xEEC8FBFF.toInt(), 0xCCFFF3B0.toInt())
                        2 -> intArrayOf(0xEEFFC8F0.toInt(), 0xCCB07CFF.toInt())
                        else -> intArrayOf(0xEEFF9AC8.toInt(), 0xEEFFD36A.toInt(), 0xEE7FE6F0.toInt(), 0xEEB07CFF.toInt())
                    }
                    shader.shader = LinearGradient(0f, 0f, len, 0f, cols, null, Shader.TileMode.CLAMP)
                    shader.alpha = (255 * alpha).toInt()
                    c.drawOval(rect, shader)
                    shader.shader = null
                    shader.alpha = 255
                    stroke.color = a(if (stage == 3) 0xFFFFD36A.toInt() else 0x99FFFFFF.toInt())
                    stroke.strokeWidth = 1.2f * s
                    c.drawOval(rect, stroke)
                    stroke.color = a(0x66FFFFFF)
                    c.drawLine(len * 0.1f, 0f, len * 0.85f, 0f, stroke)
                    c.restore()
                }
                c.restore()
            }
        }

        // kuyruk: kabarık, ucu parlayan; bitirilen bölgelerin renkleri üstünde boncuk
        val wag = sin(time * 4f) * 3f
        path.reset()
        path.moveTo(px(60f), py(78f))
        path.cubicTo(px(80f), py(82f), px(88f + wag * 0.3f), py(68f), px(84f + wag), py(52f))
        stroke.color = a(line)
        stroke.strokeWidth = 9.5f * s
        c.drawPath(path, stroke)
        stroke.color = a(mid)
        stroke.strokeWidth = 7f * s
        c.drawPath(path, stroke)
        glow(c, px(84f + wag), py(50f), 11f * s, aura, 0.7f * alpha)
        fill.color = a(light)
        c.drawCircle(px(84f + wag), py(50f), 6.5f * s, fill)
        val bx = floatArrayOf(66f, 75f, 82f, 85f, 85f)
        val by = floatArrayOf(80f, 79f, 74f, 67f, 60f)
        for (k in 0 until 5) {
            if (k >= tail.size || tail[k] == 0) continue
            val t = k / 4f
            val x = px(bx[k] + wag * t)
            val y = py(by[k])
            glow(c, x, y, 4.5f * s, tail[k], 0.6f * alpha)
            fill.color = a(tail[k])
            c.drawCircle(x, y, 2.4f * s, fill)
        }

        // gövde ve ayaklar
        rect.set(px(50f) - 15f * s, py(70f) - 14f * s, px(50f) + 15f * s, py(70f) + 14f * s)
        shader.shader = RadialGradient(px(46f), py(64f), 18f * s, intArrayOf(light, mid, deep), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        shader.alpha = (255 * alpha).toInt()
        c.drawOval(rect, shader)
        shader.shader = null
        shader.alpha = 255
        stroke.color = a(line)
        stroke.strokeWidth = 1.4f * s
        c.drawOval(rect, stroke)
        fill.color = a(mid)
        for (side in intArrayOf(-1, 1)) {
            rect.set(px(50f + side * 7f) - 5f * s, py(82f) - 3.5f * s, px(50f + side * 7f) + 5f * s, py(82f) + 3.5f * s)
            c.drawOval(rect, fill)
            c.drawOval(rect, stroke)
        }
        // göğüs tüyü
        path.reset()
        path.moveTo(px(43f), py(60f))
        path.lineTo(px(46f), py(68f))
        path.lineTo(px(48f), py(63f))
        path.lineTo(px(50f), py(70f))
        path.lineTo(px(52f), py(63f))
        path.lineTo(px(54f), py(68f))
        path.lineTo(px(57f), py(60f))
        path.close()
        fill.color = a(0xFFEFFFFF.toInt())
        c.drawPath(path, fill)

        if (scarf) {
            stroke.color = a(0xFFFF5A6E.toInt())
            stroke.strokeWidth = 4.5f * s
            c.drawLine(px(38f), py(58f), px(62f), py(58f), stroke)
            c.drawLine(px(58f), py(58f), px(63f), py(70f), stroke)
        }

        // kulaklar: büyük, sivri; iç tarafı aşamaya göre renklenir
        val spread = ears * 18f
        for (side in intArrayOf(-1, 1)) {
            c.save()
            c.translate(px(50f + side * 15f), py(30f))
            c.rotate(side * f * (18f + spread))
            c.scale(side * f, 1f)
            path.reset()
            path.moveTo(-9f * s, 4f * s)
            path.quadTo(-2f * s, -18f * s, 6f * s, -30f * s)
            path.quadTo(12f * s, -10f * s, 10f * s, 6f * s)
            path.close()
            fill.color = a(mid)
            c.drawPath(path, fill)
            stroke.color = a(line)
            stroke.strokeWidth = 1.4f * s
            c.drawPath(path, stroke)
            path.reset()
            path.moveTo(-4f * s, 2f * s)
            path.quadTo(0f, -13f * s, 5f * s, -22f * s)
            path.quadTo(8f * s, -8f * s, 6f * s, 3f * s)
            path.close()
            fill.color = a(innerEar)
            c.drawPath(path, fill)
            c.restore()
        }

        // baş
        shader.shader = RadialGradient(px(43f), py(30f), 30f * s, intArrayOf(light, mid, deep), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        shader.alpha = (255 * alpha).toInt()
        c.drawCircle(px(50f), py(40f), 22f * s, shader)
        shader.shader = null
        shader.alpha = 255
        stroke.color = a(line)
        stroke.strokeWidth = 1.4f * s
        c.drawCircle(px(50f), py(40f), 22f * s, stroke)
        // yanak tüyleri
        fill.color = a(mid)
        for (side in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(px(50f + side * 20f), py(40f))
            path.lineTo(px(50f + side * 26f), py(46f))
            path.lineTo(px(50f + side * 19f), py(48f))
            path.close()
            c.drawPath(path, fill)
        }

        // filiz
        stroke.color = a(0xFF3FA36B.toInt())
        stroke.strokeWidth = 1.6f * s
        c.drawLine(px(50f), py(19f), px(51f), py(12f), stroke)
        for (side in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(px(51f), py(13f))
            path.quadTo(px(51f + side * 3f), py(4f), px(51f + side * 11f), py(6f))
            path.quadTo(px(51f + side * 8f), py(14f), px(51f), py(13f))
            fill.color = a(if (stage >= 1) 0xFF8CF5B0.toInt() else 0xFF7BE495.toInt())
            c.drawPath(path, fill)
        }
        if (stage >= 1) glow(c, px(51f), py(10f), 8f * s, 0xFF8CF5B0.toInt(), 0.5f * alpha)

        // taç (Ustalık)
        if (stage >= 3) {
            path.reset()
            path.moveTo(px(41f), py(22f))
            path.lineTo(px(40f), py(12f))
            path.lineTo(px(45f), py(17f))
            path.lineTo(px(50f), py(9f))
            path.lineTo(px(55f), py(17f))
            path.lineTo(px(60f), py(12f))
            path.lineTo(px(59f), py(22f))
            path.close()
            glow(c, px(50f), py(16f), 12f * s, 0xFFFFD36A.toInt(), 0.6f * alpha)
            fill.color = a(0xFFFFD36A.toInt())
            c.drawPath(path, fill)
            stroke.color = a(0xFFC98A1E.toInt())
            stroke.strokeWidth = 1.2f * s
            c.drawPath(path, stroke)
            fill.color = a(0xFFFF8FB8.toInt())
            c.drawCircle(px(50f), py(17f), 1.8f * s, fill)
        }

        // gözler: iri, parlak
        val blink = eyes == 0 && (time % 4.3f) < 0.12f
        for (side in intArrayOf(-1, 1)) {
            val ex = px(50f + side * 9f) + f * 1.2f * s
            val ey = py(42f)
            when {
                eyes == 1 || blink -> {
                    stroke.color = a(0xFF0E4F66.toInt())
                    stroke.strokeWidth = 1.8f * s
                    rect.set(ex - 5f * s, ey - 3f * s, ex + 5f * s, ey + 5f * s)
                    c.drawArc(rect, 200f, 140f, false, stroke)
                }
                eyes == 2 -> {
                    fill.color = a(Color.WHITE)
                    c.drawCircle(ex, ey, 7.5f * s, fill)
                    fill.color = a(0xFF0E4F66.toInt())
                    c.drawCircle(ex, ey + 0.5f * s, 3f * s, fill)
                    fill.color = a(Color.WHITE)
                    c.drawCircle(ex + 1f * s, ey - 0.8f * s, 1f * s, fill)
                }
                else -> {
                    rect.set(ex - 6f * s, ey - 7.5f * s, ex + 6f * s, ey + 7.5f * s)
                    shader.shader = LinearGradient(0f, ey - 7.5f * s, 0f, ey + 7.5f * s, 0xFF0B3A50.toInt(), 0xFF2FB8C8.toInt(), Shader.TileMode.CLAMP)
                    shader.alpha = (255 * alpha).toInt()
                    c.drawOval(rect, shader)
                    shader.shader = null
                    shader.alpha = 255
                    fill.color = a(0xFF082636.toInt())
                    c.drawCircle(ex, ey - 0.5f * s, 3f * s, fill)
                    fill.color = a(Color.WHITE)
                    c.drawCircle(ex + 2f * s, ey - 3.5f * s, 2.4f * s, fill)
                    c.drawCircle(ex - 2f * s, ey + 3f * s, 1.1f * s, fill)
                    // kirpik
                    stroke.color = a(0xFF0B3A50.toInt())
                    stroke.strokeWidth = 1.2f * s
                    val ox = ex + side * 5.5f * s * f
                    c.drawLine(ox, ey - 5f * s, ox + side * f * 2.5f * s, ey - 7.5f * s, stroke)
                }
            }
        }
        fill.color = a(0x99FF9AB8.toInt())
        c.drawCircle(px(37f), py(49f), 3.2f * s, fill)
        c.drawCircle(px(63f), py(49f), 3.2f * s, fill)
        stroke.color = a(0xFF0E4F66.toInt())
        stroke.strokeWidth = 1.3f * s
        if (eyes == 2) {
            fill.color = a(0xFF0E4F66.toInt())
            c.drawCircle(px(50f), py(53f), 1.8f * s, fill)
        } else {
            rect.set(px(50f) - 3f * s, py(49.5f), px(50f) + 3f * s, py(54f))
            c.drawArc(rect, 20f, 140f, false, stroke)
        }

        // İleri ve Ustalık: etrafında dönen pırıltılar
        if (stage >= 2) {
            val n = if (stage == 2) 3 else 5
            for (k in 0 until n) {
                val ang = time * 1.4f + k * (2f * PI.toFloat() / n)
                val sx = cx + cos(ang) * r * 1.9f
                val sy = py(50f) + sin(ang) * r * 1.3f
                val tw = 0.5f + 0.5f * sin(time * 5f + k)
                glow(c, sx, sy, 4f * s, aura, 0.8f * tw * alpha)
                fill.color = a(Color.WHITE)
                c.drawCircle(sx, sy, 1.1f * s * (0.6f + tw), fill)
            }
        }
    }

    /** Krem konuşma balonu; [tx],[ty] balonun kuyruğunun değdiği nokta. Ekran kenarına sığacak şekilde kayar. */
    fun bubble(c: Canvas, s: String, tx: Float, ty: Float, size: Float, screenW: Float, alpha: Float) {
        if (alpha <= 0f) return
        textPaint.textSize = size
        textPaint.letterSpacing = 0f
        val tw = textPaint.measureText(s)
        val pad = size * 0.7f
        val bw = tw + pad * 2
        val bh = size * 2f
        val left = (tx - bw / 2).coerceIn(size * 0.6f, screenW - bw - size * 0.6f)
        val top = ty - size * 0.6f - bh
        fill.color = withAlpha(0x55000000, alpha)
        rect.set(left, top + size * 0.25f, left + bw, top + bh + size * 0.25f)
        c.drawRoundRect(rect, bh / 2, bh / 2, fill)
        fill.color = withAlpha(0xFFFFFBF2.toInt(), alpha)
        rect.set(left, top, left + bw, top + bh)
        c.drawRoundRect(rect, bh / 2, bh / 2, fill)
        path.reset()
        path.moveTo(tx - size * 0.45f, top + bh - 1f)
        path.lineTo(tx, ty)
        path.lineTo(tx + size * 0.45f, top + bh - 1f)
        path.close()
        c.drawPath(path, fill)
        text(c, s, left + bw / 2, top + bh / 2, size, withAlpha(0xFF2A2450.toInt(), alpha))
    }

    /** Gölge yaratık. Avcı gölge kırmızımsı ve boynuzludur; uyurken gözleri kapalıdır. */
    fun shadowCreature(
        c: Canvas, cx: Float, cy: Float, r: Float, lookX: Float, lookY: Float, seed: Int,
        hunter: Boolean = false, awake: Boolean = true,
    ) {
        val rim = if (hunter) 0xFFD0453A.toInt() else 0xFF7B4FBF.toInt()
        glow(c, cx, cy, r * (if (hunter && awake) 2.6f else 2.2f), rim, if (hunter && awake) 0.5f else 0.35f)
        val wob = sin(time * (if (hunter && awake) 10f else 6f) + seed) * r * 0.08f
        if (hunter) {
            fill.color = 0xF01C0A12.toInt()
            for (side in intArrayOf(-1, 1)) {
                path.reset()
                path.moveTo(cx + side * r * 0.25f, cy - r * 0.8f)
                path.lineTo(cx + side * r * 0.62f, cy - r * 1.35f)
                path.lineTo(cx + side * r * 0.7f, cy - r * 0.55f)
                path.close()
                c.drawPath(path, fill)
            }
        }
        path.reset()
        rect.set(cx - r, cy - r, cx + r, cy + r)
        path.moveTo(cx - r, cy)
        path.arcTo(rect, 180f, 180f)
        path.lineTo(cx + r, cy + r * 0.85f)
        val seg = 2 * r / 3
        for (i in 0 until 3) {
            val x1 = cx + r - seg * (i + 0.5f)
            val x2 = cx + r - seg * (i + 1)
            path.quadTo(x1, cy + r * 1.25f + if (i % 2 == 0) wob else -wob, x2, cy + r * 0.85f)
        }
        path.close()
        fill.color = if (hunter) 0xF01C0A12.toInt() else 0xF0120A22.toInt()
        c.drawPath(path, fill)
        stroke.strokeWidth = r * 0.08f
        stroke.color = withAlpha(rim, 0.6f)
        c.drawPath(path, stroke)
        val len = kotlin.math.max(0.001f, kotlin.math.hypot(lookX, lookY))
        val ex = lookX / len * r * 0.15f
        val ey = lookY / len * r * 0.12f
        for (side in intArrayOf(-1, 1)) {
            val x = cx + side * r * 0.35f + ex
            val y = cy - r * 0.1f + ey
            if (hunter && !awake) {
                stroke.color = 0xAAFF9A8A.toInt()
                stroke.strokeWidth = r * 0.07f
                rect.set(x - r * 0.13f, y - r * 0.1f, x + r * 0.13f, y + r * 0.08f)
                c.drawArc(rect, 20f, 140f, false, stroke)
                continue
            }
            val eye = if (hunter) 0xFFFF6A3D.toInt() else 0xFFD9B3FF.toInt()
            glow(c, x, y, r * 0.45f, eye, 0.7f)
            fill.color = if (hunter) 0xFFFFC2A0.toInt() else 0xFFF1DDFF.toInt()
            rect.set(x - r * 0.13f, y - r * 0.09f, x + r * 0.13f, y + r * 0.09f)
            c.drawOval(rect, fill)
        }
        if (hunter && !awake) {
            val zt = frac(time * 0.5f + seed * 0.1f)
            text(c, "z", cx + r * 0.7f + zt * r * 0.4f, cy - r * 1.1f - zt * r * 0.6f, r * 0.45f, withAlpha(0xFFFFC2A0.toInt(), 1f - zt))
        }
    }

    // ---------------------------------------------------------------- arayüz

    fun starPath(cx: Float, cy: Float, r: Float, inner: Float = 0.48f): Path {
        path.reset()
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5
            val rr = if (i % 2 == 0) r else r * inner
            val x = cx + (cos(a) * rr).toFloat()
            val y = cy + (sin(a) * rr).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Kalın beyaz kenarlı, iç yıldızlı oyun yıldızı. */
    /** [filled] false ise kazanılmamış yıldız: aynı biçim, soluk ten rengi. */
    fun chunkyStar(c: Canvas, cx: Float, cy: Float, r: Float, filled: Boolean = true) {
        val round = CornerPathEffect(r * 0.18f)
        fill.pathEffect = round
        shader.pathEffect = round
        fill.color = 0x33000000
        c.drawPath(starPath(cx, cy + r * 0.08f, r * 1.12f), fill)
        fill.color = Color.WHITE
        c.drawPath(starPath(cx, cy, r * 1.12f), fill)
        if (filled) {
            shader.shader = LinearGradient(cx, cy - r, cx, cy + r, 0xFFFFE066.toInt(), 0xFFFFB000.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(starPath(cx, cy, r), shader)
            shader.shader = null
            fill.color = 0xFFFF9F1C.toInt()
        } else {
            fill.color = 0xFFDCCBAA.toInt()
            c.drawPath(starPath(cx, cy, r), fill)
            fill.color = 0xFFCBB892.toInt()
        }
        c.drawPath(starPath(cx, cy + r * 0.04f, r * 0.45f), fill)
        fill.pathEffect = null
        shader.pathEffect = null
    }

    fun smallStar(c: Canvas, cx: Float, cy: Float, r: Float, filled: Boolean) {
        val round = CornerPathEffect(r * 0.2f)
        if (filled) {
            fill.pathEffect = round
            fill.color = 0xFFFFC93D.toInt()
            c.drawPath(starPath(cx, cy, r), fill)
            fill.pathEffect = null
        } else {
            stroke.pathEffect = round
            stroke.strokeWidth = r * 0.14f
            stroke.color = 0x66FFFFFF
            c.drawPath(starPath(cx, cy, r), stroke)
            stroke.pathEffect = null
        }
    }

    fun hexPath(cx: Float, cy: Float, r: Float): Path {
        path.reset()
        for (i in 0 until 6) {
            val a = Math.toRadians(-90.0 + i * 60.0)
            val x = cx + (cos(a) * r).toFloat()
            val y = cy + (sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    fun lock(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.strokeWidth = s * 0.14f
        stroke.color = color
        rect.set(cx - s * 0.26f, cy - s * 0.6f, cx + s * 0.26f, cy + s * 0.02f)
        c.drawArc(rect, 180f, 180f, false, stroke)
        fill.color = color
        rect.set(cx - s * 0.42f, cy - s * 0.25f, cx + s * 0.42f, cy + s * 0.42f)
        c.drawRoundRect(rect, s * 0.1f, s * 0.1f, fill)
    }

    /** Altın veya kırmızı hap düğme: beyaz kenar, alt gölge, gölgeli beyaz yazı. */
    fun pill(c: Canvas, r: RectF, top: Int, bottom: Int, label: String, pressed: Boolean, enabled: Boolean = true) {
        val rr = RectF(r)
        if (pressed) rr.offset(0f, r.height() * 0.05f)
        val rad = rr.height() / 2
        val a = if (enabled) 1f else 0.45f
        fill.color = withAlpha(0x55000000, a)
        rect.set(rr.left, rr.top + rr.height() * 0.1f, rr.right, rr.bottom + rr.height() * 0.1f)
        if (!pressed) c.drawRoundRect(rect, rad, rad, fill)
        fill.color = withAlpha(0xFFFFF8EA.toInt(), a)
        c.drawRoundRect(rr, rad, rad, fill)
        val inset = rr.height() * 0.09f
        rect.set(rr.left + inset, rr.top + inset, rr.right - inset, rr.bottom - inset)
        shader.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP)
        shader.alpha = (255 * a).toInt()
        c.drawRoundRect(rect, rad - inset, rad - inset, shader)
        shader.shader = null
        shader.alpha = 255
        fill.color = withAlpha(Color.WHITE, 0.25f * a)
        rect.set(rect.left + rad * 0.5f, rect.top + inset * 0.6f, rect.right - rad * 0.5f, rect.top + rect.height() * 0.38f)
        c.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
        text(c, label, rr.centerX(), rr.centerY(), rr.height() * 0.42f, withAlpha(Color.WHITE, a), shadow = withAlpha(0x99682300.toInt(), a))
    }

    /** Altın yuvarlak simge düğmesi. */
    fun roundButton(c: Canvas, cx: Float, cy: Float, rad: Float, pressed: Boolean, top: Int = 0xFFFFC93D.toInt(), bottom: Int = 0xFFFF9A1F.toInt()) {
        val y = if (pressed) cy + rad * 0.06f else cy
        if (!pressed) {
            fill.color = 0x55000000
            c.drawCircle(cx, cy + rad * 0.12f, rad, fill)
        }
        fill.color = 0xFFFFF8EA.toInt()
        c.drawCircle(cx, y, rad, fill)
        shader.shader = LinearGradient(0f, y - rad, 0f, y + rad, top, bottom, Shader.TileMode.CLAMP)
        c.drawCircle(cx, y, rad * 0.84f, shader)
        shader.shader = null
    }

    /** Oyun içi saydam halka düğme. */
    fun ghostButton(c: Canvas, cx: Float, cy: Float, rad: Float, pressed: Boolean) {
        fill.color = if (pressed) 0x33FFFFFF else 0x14FFFFFF
        c.drawCircle(cx, cy, rad, fill)
        stroke.color = 0xDDFFFFFF.toInt()
        stroke.strokeWidth = rad * 0.08f
        c.drawCircle(cx, cy, rad, stroke)
    }

    // ---------------------------------------------------------------- simgeler

    fun iconBack(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.strokeWidth = s * 0.12f
        stroke.color = color
        path.reset()
        path.moveTo(cx + s * 0.12f, cy - s * 0.26f)
        path.lineTo(cx - s * 0.14f, cy)
        path.lineTo(cx + s * 0.12f, cy + s * 0.26f)
        c.drawPath(path, stroke)
    }

    fun iconRestart(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val rr = s * 0.28f
        stroke.strokeWidth = s * 0.1f
        stroke.color = color
        rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
        c.drawArc(rect, -50f, 290f, false, stroke)
        val a = Math.toRadians(-50.0)
        val ex = cx + (cos(a) * rr).toFloat()
        val ey = cy + (sin(a) * rr).toFloat()
        fill.color = color
        path.reset()
        path.moveTo(ex + s * 0.15f, ey - s * 0.02f)
        path.lineTo(ex - s * 0.1f, ey - s * 0.12f)
        path.lineTo(ex + s * 0.02f, ey + s * 0.14f)
        path.close()
        c.drawPath(path, fill)
    }

    fun iconList(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        for (i in -1..1) {
            val y = cy + i * s * 0.26f
            c.drawCircle(cx - s * 0.26f, y, s * 0.08f, fill)
            rect.set(cx - s * 0.12f, y - s * 0.08f, cx + s * 0.34f, y + s * 0.08f)
            c.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
        }
    }

    fun iconNext(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        fill.pathEffect = CornerPathEffect(s * 0.08f)
        path.reset()
        path.moveTo(cx - s * 0.32f, cy - s * 0.1f)
        path.lineTo(cx + s * 0.02f, cy - s * 0.1f)
        path.lineTo(cx + s * 0.02f, cy - s * 0.3f)
        path.lineTo(cx + s * 0.36f, cy)
        path.lineTo(cx + s * 0.02f, cy + s * 0.3f)
        path.lineTo(cx + s * 0.02f, cy + s * 0.1f)
        path.lineTo(cx - s * 0.32f, cy + s * 0.1f)
        path.close()
        c.drawPath(path, fill)
        fill.pathEffect = null
    }

    fun iconHome(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        fill.pathEffect = CornerPathEffect(s * 0.06f)
        path.reset()
        path.moveTo(cx - s * 0.36f, cy - s * 0.02f)
        path.lineTo(cx, cy - s * 0.36f)
        path.lineTo(cx + s * 0.36f, cy - s * 0.02f)
        path.lineTo(cx + s * 0.26f, cy - s * 0.02f)
        path.lineTo(cx + s * 0.26f, cy + s * 0.32f)
        path.lineTo(cx - s * 0.26f, cy + s * 0.32f)
        path.lineTo(cx - s * 0.26f, cy - s * 0.02f)
        path.close()
        c.drawPath(path, fill)
        fill.pathEffect = null
    }

    fun iconMap(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val hex = hexPath(cx, cy - s * 0.02f, s * 0.36f)
        stroke.color = color
        stroke.strokeWidth = s * 0.1f
        c.drawPath(hex, stroke)
        fill.color = color
        c.drawCircle(cx, cy - s * 0.02f, s * 0.1f, fill)
    }

    fun iconSound(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, on: Boolean) {
        fill.color = color
        fill.pathEffect = CornerPathEffect(s * 0.04f)
        path.reset()
        path.moveTo(cx - s * 0.34f, cy - s * 0.12f)
        path.lineTo(cx - s * 0.16f, cy - s * 0.12f)
        path.lineTo(cx + s * 0.06f, cy - s * 0.32f)
        path.lineTo(cx + s * 0.06f, cy + s * 0.32f)
        path.lineTo(cx - s * 0.16f, cy + s * 0.12f)
        path.lineTo(cx - s * 0.34f, cy + s * 0.12f)
        path.close()
        c.drawPath(path, fill)
        fill.pathEffect = null
        stroke.color = color
        stroke.strokeWidth = s * 0.08f
        if (on) {
            rect.set(cx - s * 0.06f, cy - s * 0.2f, cx + s * 0.3f, cy + s * 0.2f)
            c.drawArc(rect, -50f, 100f, false, stroke)
            rect.set(cx - s * 0.12f, cy - s * 0.34f, cx + s * 0.42f, cy + s * 0.34f)
            c.drawArc(rect, -50f, 100f, false, stroke)
        } else {
            c.drawLine(cx + s * 0.16f, cy - s * 0.12f, cx + s * 0.38f, cy + s * 0.12f, stroke)
            c.drawLine(cx + s * 0.38f, cy - s * 0.12f, cx + s * 0.16f, cy + s * 0.12f, stroke)
        }
    }

    fun diamond(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        path.reset()
        path.moveTo(cx, cy - r)
        path.lineTo(cx + r, cy)
        path.lineTo(cx, cy + r)
        path.lineTo(cx - r, cy)
        path.close()
        c.drawPath(path, fill)
    }
}
