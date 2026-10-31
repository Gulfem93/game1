using System;
using System.Collections.Generic;
using System.Text;

public class Spec {
    public string Theme = "forest";
    public int Seed = 1, Tries = 3000;
    public double Wall = 0.3; public int Thorns = 3;
    public bool Door = false; public int Shadows = 0, Hunters = 0;
    public double Island = 0.22, Lily = 0.4; public bool RequireDiff = false; public int MaxLilies = 10;
    public double Ice = 0, Wind = 0, Void = 0; public int MinForced = 0;
    public int MinMoves = 20, MaxMoves = 60;
}

public class Result {
    public string[] Map; public int Moves, Forced, States, Diff; public double Score;
}

public static class LevelGen {
    public const int W = 7, H = 10;
    static readonly int[] DX = { 1, -1, 0, 0 }, DY = { 0, 0, 1, -1 };
    static Random rnd;
    const int StateCap = 300000;

    // mode 0: normal, 1: nilüferler kalıcı, 2: nilüferler su, 3: buz/rüzgâr düz zemin
    static bool Sim(char[] m, int mode, int[] lilyIdx, int[] shardIdx, int all,
                    int pos, int sm, int cr, long sunk, int k,
                    out int np, out int nsm, out int ncr, out long nsunk, out int forced, out bool win) {
        np = pos; nsm = sm; ncr = cr; nsunk = sunk; forced = 0; win = false;
        int dx = DX[k], dy = DY[k];
        bool moved = false;
        for (int it = 0; it <= 40; it++) {
            int x = np % W, y = np / W, nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= W || ny >= H) return moved;
            int q = ny * W + nx;
            char ch = m[q];
            if (ch == '#' || ch == '~') return moved;
            if (ch == 'o' && (mode == 2 || ((nsunk >> lilyIdx[q]) & 1) == 1)) return moved;
            if (ch == 'D' && ncr == 0) return moved;
            if (ch == '^') return false;
            if (mode == 0 && lilyIdx[np] >= 0) nsunk |= 1L << lilyIdx[np];
            np = q;
            if (moved) forced++;
            moved = true;
            if (shardIdx[q] >= 0) nsm |= 1 << shardIdx[q];
            if (ch == 'C') ncr = 1;
            if (ch == 'E' && nsm == all) { win = true; return true; }
            if (mode == 3) return true;
            if (ch == '=') { }
            else if (ch == '>') { dx = 1; dy = 0; }
            else if (ch == '<') { dx = -1; dy = 0; }
            else if (ch == 'n') { dx = 0; dy = -1; }
            else if (ch == 'u') { dx = 0; dy = 1; }
            else return true;
        }
        return false;
    }

    public static int Solve(char[] m, int mode, out int forcedOnPath, out int states) {
        forcedOnPath = 0; states = 0;
        int[] lilyIdx = new int[W * H], shardIdx = new int[W * H];
        int lilies = 0, shards = 0, start = -1;
        for (int p = 0; p < W * H; p++) {
            lilyIdx[p] = -1; shardIdx[p] = -1;
            if (m[p] == 'o') lilyIdx[p] = lilies++;
            if (m[p] == '*') shardIdx[p] = shards++;
            if (m[p] == 'L') start = p;
        }
        if (start < 0 || lilies > 40) return -1;
        int all = (1 << shards) - 1;
        var dist = new Dictionary<long, int>();
        var parent = new Dictionary<long, long>();
        var edgeF = new Dictionary<long, int>();
        var q = new Queue<long>();
        long s0 = start;
        dist[s0] = 0; q.Enqueue(s0);
        while (q.Count > 0) {
            long s = q.Dequeue();
            int d = dist[s];
            int pos = (int)(s & 127), sm = (int)((s >> 7) & 7), cr = (int)((s >> 10) & 1);
            long sunk = s >> 11;
            for (int k = 0; k < 4; k++) {
                int np, nsm, ncr, f; long nsunk; bool win;
                if (!Sim(m, mode, lilyIdx, shardIdx, all, pos, sm, cr, sunk, k, out np, out nsm, out ncr, out nsunk, out f, out win)) continue;
                long ns = (nsunk * 2048L) + (ncr * 1024L) + (nsm * 128L) + np;
                if (win) {
                    int tot = f;
                    long c = s;
                    while (parent.ContainsKey(c)) { tot += edgeF[c]; c = parent[c]; }
                    forcedOnPath = tot;
                    states = dist.Count;
                    return d + 1;
                }
                if (dist.ContainsKey(ns)) continue;
                dist[ns] = d + 1; parent[ns] = s; edgeF[ns] = f;
                if (dist.Count > StateCap) return -2;
                q.Enqueue(ns);
            }
        }
        states = dist.Count;
        return -1;
    }

    static int Man(int a, int b) { return Math.Abs(a % W - b % W) + Math.Abs(a / W - b / W); }

    static char RandWind() {
        switch (rnd.Next(4)) { case 0: return '>'; case 1: return '<'; case 2: return 'n'; default: return 'u'; }
    }

    static char[] Build(Spec s) {
        var m = new char[W * H];
        for (int i = 0; i < m.Length; i++) m[i] = '.';
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int p = y * W + x;
            double r = rnd.NextDouble();
            switch (s.Theme) {
                case "forest": case "cave":
                    if (r < s.Wall) m[p] = '#';
                    break;
                case "lake":
                    if (y <= 1 || y >= 8) { if (r < s.Wall) m[p] = '#'; }
                    else if (r < s.Island) m[p] = '.';
                    else m[p] = rnd.NextDouble() < s.Lily ? 'o' : '~';
                    break;
                case "snow":
                    if (r < s.Ice) m[p] = '=';
                    else if (r < s.Ice + s.Wall) m[p] = '#';
                    break;
                case "cloud":
                    if (r < s.Void) m[p] = '~';
                    else if (r < s.Void + s.Wind) m[p] = RandWind();
                    else if (r < s.Void + s.Wind + s.Ice) m[p] = '=';
                    else if (r < s.Void + s.Wind + s.Ice + s.Wall) m[p] = '#';
                    break;
            }
        }
        int doorRow = -1;
        if (s.Door) {
            doorRow = s.Theme == "lake" ? 7 : 4 + rnd.Next(2);
            for (int x = 0; x < W; x++) m[doorRow * W + x] = '#';
            m[doorRow * W + rnd.Next(W)] = 'D';
        }
        int L = rnd.Next(2) * W + rnd.Next(W);
        m[L] = 'L';
        int E = (8 + rnd.Next(2)) * W + rnd.Next(W);
        if (E == L) return null;
        m[E] = 'E';
        if (s.Door) {
            var cand = new List<int>();
            for (int p = 0; p < doorRow * W; p++) if (m[p] == '.' && Man(p, L) >= 3) cand.Add(p);
            if (cand.Count == 0) return null;
            m[cand[rnd.Next(cand.Count)]] = 'C';
        }
        // dikenler
        for (int t = 0; t < s.Thorns; t++) {
            var cand = new List<int>();
            for (int p = 0; p < m.Length; p++) if (m[p] == '.' && Man(p, L) >= 2 && Man(p, E) >= 2) cand.Add(p);
            if (cand.Count == 0) break;
            m[cand[rnd.Next(cand.Count)]] = '^';
        }
        // ışık parçaları
        var shards = new List<int>();
        for (int t = 0; t < 3; t++) {
            var cand = new List<int>();
            for (int p = 0; p < m.Length; p++) {
                bool okTile = m[p] == '.' || (s.Theme == "snow" && m[p] == '=');
                if (!okTile || Man(p, L) < 4) continue;
                if (s.Theme == "lake" && t < 2 && (p / W < 2 || p / W > 6)) continue;
                bool far = true;
                foreach (int o in shards) if (Man(p, o) < 3) far = false;
                if (far) cand.Add(p);
            }
            if (cand.Count == 0) return null;
            int c = cand[rnd.Next(cand.Count)];
            m[c] = '*';
            shards.Add(c);
        }
        int lilyCount = 0;
        foreach (char ch in m) if (ch == 'o') lilyCount++;
        if (lilyCount > s.MaxLilies) return null;
        // devriye gölgeleri: uzun düz zemin şeritlerine
        var used = new bool[W * H];
        for (int t = 0; t < s.Shadows; t++) {
            var runs = new List<int[]>();
            for (int o = 0; o < 2; o++) {
                int dx = o == 0 ? 1 : 0, dy = o == 0 ? 0 : 1;
                for (int p = 0; p < m.Length; p++) {
                    int x = p % W, y = p / W;
                    int px = x - dx, py = y - dy;
                    if (px >= 0 && py >= 0 && IsRun(m[py * W + px])) continue;
                    var cells = new List<int>();
                    int cx = x, cy = y;
                    while (cx < W && cy < H && IsRun(m[cy * W + cx])) { cells.Add(cy * W + cx); cx += dx; cy += dy; }
                    if (cells.Count < 3) continue;
                    bool bad = false;
                    foreach (int c in cells) if (used[c] || Man(c, L) <= 2) bad = true;
                    // şeridin uçları çıkmaz sokak olmamalı: uç karenin şerit dışında en az bir açık komşusu olsun
                    foreach (int end in new int[] { cells[0], cells[cells.Count - 1] }) {
                        int open = 0;
                        for (int k = 0; k < 4; k++) {
                            int nx = end % W + DX[k], ny = end / W + DY[k];
                            if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                            int n = ny * W + nx;
                            if (cells.Contains(n)) continue;
                            char ch = m[n];
                            if (ch != '#' && ch != '~' && ch != '^') open++;
                        }
                        if (open == 0) bad = true;
                    }
                    if (bad) continue;
                    runs.Add(new int[] { o }.Concat(cells));
                }
            }
            if (runs.Count == 0) return null;
            int[] run = runs[rnd.Next(runs.Count)];
            var dots = new List<int>();
            for (int i = 1; i < run.Length; i++) { used[run[i]] = true; if (m[run[i]] == '.') dots.Add(run[i]); }
            if (dots.Count == 0) return null;
            m[dots[rnd.Next(dots.Count)]] = run[0] == 0 ? 'H' : 'V';
        }
        // avcılar
        for (int t = 0; t < s.Hunters; t++) {
            var cand = new List<int>();
            for (int p = 0; p < m.Length; p++) {
                if (m[p] != '.' || used[p] || Man(p, L) < 6) continue;
                bool ok = Man(p, E) >= 3;
                for (int o = 0; o < m.Length; o++) if ((m[o] == '*' || m[o] == 'C' || m[o] == 'A') && Man(p, o) < 3) ok = false;
                if (ok) cand.Add(p);
            }
            if (cand.Count == 0) return null;
            m[cand[rnd.Next(cand.Count)]] = 'A';
        }
        return m;
    }

    static bool IsRun(char c) { return c == '.'; }

    /** Rüzgâr zincirlerinde sonsuz döngü var mı (statik harita üzerinde). */
    static bool HasWindLoop(char[] m) {
        for (int p = 0; p < m.Length; p++) {
            if (!IsWind(m[p])) continue;
            var seen = new HashSet<int>();
            int cur = p;
            int dx = 0, dy = 0;
            for (int it = 0; it < 60; it++) {
                char ch = m[cur];
                if (IsWind(ch)) { WindDir(ch, out dx, out dy); }
                else if (ch != '=') break;
                int key = cur * 4 + (dx == 1 ? 0 : dx == -1 ? 1 : dy == 1 ? 2 : 3);
                if (!seen.Add(key)) return true;
                int nx = cur % W + dx, ny = cur / W + dy;
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) break;
                char nc = m[ny * W + nx];
                if (nc == '#' || nc == '~' || nc == 'D') break;
                cur = ny * W + nx;
            }
        }
        return false;
    }

    static bool IsWind(char c) { return c == '>' || c == '<' || c == 'n' || c == 'u'; }

    static void WindDir(char c, out int dx, out int dy) {
        dx = c == '>' ? 1 : c == '<' ? -1 : 0;
        dy = c == 'u' ? 1 : c == 'n' ? -1 : 0;
    }

    static int[] Concat(this int[] a, List<int> b) {
        var r = new int[a.Length + b.Count];
        a.CopyTo(r, 0);
        b.CopyTo(r, a.Length);
        return r;
    }

    public static Result Make(Spec s) {
        rnd = new Random(s.Seed);
        Result best = null;
        for (int t = 0; t < s.Tries; t++) {
            var m = Build(s);
            if (m == null || HasWindLoop(m)) continue;
            int f, st;
            int moves = Solve(m, 0, out f, out st);
            if (moves < s.MinMoves || moves > s.MaxMoves) continue;
            if (f < s.MinForced) continue;
            int diff = 0;
            if (s.Theme == "lake") {
                int f2, st2;
                if (Solve(m, 2, out f2, out st2) != -1) continue;
                int perm = Solve(m, 1, out f2, out st2);
                if (perm < 0) continue;
                diff = moves - perm;
                if (s.RequireDiff && diff <= 0) continue;
            }
            if (s.Ice + s.Wind > 0) {
                int f3, st3;
                int flat = Solve(m, 3, out f3, out st3);
                if (flat >= 0 && flat <= moves && f < s.MinForced + 2) continue;
            }
            double score = Math.Log(st + 1) * 10 + f * 6 + diff * 25 + moves * 1.5;
            if (best == null || score > best.Score) {
                var rows = new string[H];
                for (int y = 0; y < H; y++) rows[y] = new string(m, y * W, W);
                best = new Result { Map = rows, Moves = moves, Forced = f, States = st, Diff = diff, Score = score };
            }
        }
        return best;
    }

    public static string Check(string[] rows) {
        var m = new char[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) m[y * W + x] = rows[y][x];
        int f, st;
        int moves = Solve(m, 0, out f, out st);
        return "moves=" + moves + " forced=" + f + " states=" + st;
    }
}
