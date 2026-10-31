package com.lumi.kayiprenkler

import android.content.Context

/** Açık bölümler, bölüm başına en iyi yıldız sayısı (1-3) ve ses tercihi cihazda saklanır. */
class Progress(context: Context) {

    private val prefs = context.getSharedPreferences("lumi_progress", Context.MODE_PRIVATE)

    var soundOn: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) = prefs.edit().putBoolean("sound", value).apply()

    private val unlocked: Int get() = prefs.getInt("unlocked", 1)

    fun isUnlocked(i: Int) = i < unlocked

    /** Bölüm en az bir kez bitirildi mi. */
    fun hasStar(i: Int) = prefs.getBoolean("star_$i", false)

    /** Bölümde kazanılan en iyi yıldız sayısı; bitirilmediyse 0. Eski kayıtlarda bitmiş bölüm 1 sayılır. */
    fun stars(i: Int): Int = if (!hasStar(i)) 0 else prefs.getInt("stars_$i", 1)

    fun starCount() = Levels.all.indices.sumOf { stars(it) }

    val maxStars: Int get() = Levels.all.size * 3

    /** Bölümü kaydeder; yalnızca daha iyi bir sonuç eskisinin yerini alır. Yeni rekor ise true. */
    fun complete(i: Int, stars: Int): Boolean {
        val best = stars(i)
        prefs.edit()
            .putBoolean("star_$i", true)
            .putInt("stars_$i", maxOf(best, stars))
            .putInt("unlocked", maxOf(unlocked, minOf(i + 2, Levels.all.size)))
            .apply()
        return stars > best
    }

    /** Pıtır'la bağ puanı: bölüm bitirmek, 3 yıldız ve Pıtır'ı sevmek artırır. */
    var bond: Int
        get() = prefs.getInt("bond", 0)
        set(value) = prefs.edit().putInt("bond", value).apply()

    /** Bağ seviyesi 1-5: Tanışma, Arkadaş, Yol arkadaşı, Can dostu, Aile. */
    val bondLevel: Int get() = BOND_STEPS.count { bond >= it }

    /** Bütün bölümleri bitirilmiş bölge sayısı; Pıtır'ın kuyruğundaki renkli boncuklar. */
    fun regionsDone(): Int = Levels.regions.count { r -> (r.first..r.last).all { hasStar(it) } }

    /** Pıtır'ın tanıttığı canavar türleri; ilk karşılaşmada tanıtım kartı çıkar. */
    fun seenMonster(m: Monster) = prefs.getBoolean("seen_${m.name}", false)

    fun markMonster(m: Monster) = prefs.edit().putBoolean("seen_${m.name}", true).apply()

    /** "Oyna" düğmesi için: tamamlanmamış ilk açık bölüm, yoksa -1. */
    fun nextLevel(): Int = Levels.all.indices.firstOrNull { isUnlocked(it) && !hasStar(it) } ?: -1

    companion object {
        val BOND_STEPS = intArrayOf(0, 8, 25, 50, 90)
        val BOND_NAMES = listOf("Tanışma", "Arkadaş", "Yol arkadaşı", "Can dostu", "Aile")
    }
}
