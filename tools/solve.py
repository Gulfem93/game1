"""
Levels.kt içindeki bütün bölümleri çözer ve par değerlerini denetler (levelgen.cs çözücüsünün Python hâli,
Pıtır'ın yeni kareleriyle):
  ?  gizli ışık parçası: Pıtır gösterene kadar görünmez ve toplanamaz
  :  görünmez köprü: Pıtır gösterene kadar geçilemez
  P  Pıtır şalteri: Lumi geçemez; Lumi yanındaki kareye gelince Pıtır çeker, kök kapılar açılır
  G  kök kapı: şalter çekilene kadar kapalı
Pıtır'ın "gizliyi göster" yeteneği bölümde bir kez kullanılır ve bir hamle sayılır.

Kullanım: python -I tools/solve.py [Levels.kt yolu]
"""
import re
import sys
from collections import deque

W, H = 7, 10
DIRS = [(1, 0), (-1, 0), (0, 1), (0, -1)]
WIND = {'>': (1, 0), '<': (-1, 0), 'n': (0, -1), 'u': (0, 1)}


def solve(rows, allow_reveal=True):
    m = ''.join(rows)
    shard_idx, lily_idx = {}, {}
    start = None
    for p, ch in enumerate(m):
        if ch in '*?':
            shard_idx[p] = len(shard_idx)
        elif ch == 'o':
            lily_idx[p] = len(lily_idx)
        elif ch == 'L':
            start = p
    full = (1 << len(shard_idx)) - 1
    has_hidden = any(ch in '?:' for ch in m)
    levers = [p for p, ch in enumerate(m) if ch == 'P']

    def lever_near(p):
        x, y = p % W, p // W
        return any(abs(q % W - x) + abs(q // W - y) == 1 for q in levers)

    def sim(pos, sm, cr, rev, lev, sunk, k):
        dx, dy = DIRS[k]
        moved = False
        for _ in range(41):
            x, y = pos % W, pos // W
            nx, ny = x + dx, y + dy
            if not (0 <= nx < W and 0 <= ny < H):
                return (pos, sm, cr, rev, lev, sunk, False) if moved else None
            q = ny * W + nx
            ch = m[q]
            blocked = (
                ch in '#~P'
                or (ch == 'o' and (sunk >> lily_idx[q]) & 1)
                or (ch == 'D' and not cr)
                or (ch == 'G' and not lev)
                or (ch == ':' and not rev)
            )
            if blocked:
                return (pos, sm, cr, rev, lev, sunk, False) if moved else None
            if ch == '^':
                return None
            if pos in lily_idx:
                sunk |= 1 << lily_idx[pos]
            pos = q
            moved = True
            if q in shard_idx and (ch == '*' or rev):
                sm |= 1 << shard_idx[q]
            if ch == 'C':
                cr = 1
            if levers and lever_near(q):
                lev = 1
            if ch == 'E' and sm == full:
                return (pos, sm, cr, rev, lev, sunk, True)
            if ch == '=':
                continue
            if ch in WIND:
                dx, dy = WIND[ch]
                continue
            return (pos, sm, cr, rev, lev, sunk, False)
        return None

    s0 = (start, 0, 0, 0, 1 if levers and lever_near(start) else 0, 0)
    dist = {s0: 0}
    queue = deque([s0])
    while queue:
        s = queue.popleft()
        d = dist[s]
        nexts = [sim(*s, k) for k in range(4)]
        if allow_reveal and has_hidden and not s[3]:
            nexts.append(s[:3] + (1,) + s[4:] + (False,))
        for n in nexts:
            if n is None:
                continue
            if n[6]:
                return d + 1
            ns = n[:6]
            if ns not in dist:
                dist[ns] = d + 1
                queue.append(ns)
    return -1


def levels(path):
    src = open(path, encoding='utf-8').read()
    for block in re.finditer(r'LevelDef\((.*?)\n        \),', src, re.S):
        b = block.group(1)
        name = re.search(r'name = "(.*?)"', b).group(1)
        par = re.search(r'par = (\d+)', b)
        rows = re.findall(r'"([^"]{7})",', re.search(r'map = listOf\((.*?)\)', b, re.S).group(1))
        yield name, int(par.group(1)) if par else 0, rows


if __name__ == '__main__':
    path = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java/com/lumi/kayiprenkler/Levels.kt'
    bad = 0
    for i, (name, par, rows) in enumerate(levels(path)):
        got = solve(rows)
        note = ''
        if any(ch in '?:' for ch in ''.join(rows)):
            note = '  (Pıtır olmadan: %d)' % solve(rows, allow_reveal=False)
        ok = got == par
        bad += not ok
        print('%2d %-22s par=%3d çözüm=%3d %s%s' % (i + 1, name, par, got, 'ok' if ok else 'FARKLI', note))
    sys.exit(1 if bad else 0)
