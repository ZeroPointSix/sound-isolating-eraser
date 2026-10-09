package com.zeropointsix.eraser.fertilizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 榕树式“独木成林”的形状算法：粗主干、平伸的大枝与分叉、枝下垂落地的气根树干、扁平成片的树冠。
 *
 * <p>只依赖 {@link World} 接口与相对主干根部的整数坐标，不引用游戏类，可单独编译运行做形状预览。
 * 同一个种子在不同等级下生成的枝条路径互为前缀，所以升级时旧的气根仍然落在枝下。
 */
public final class BanyanShape {
    public enum Kind { LOG_X, LOG_Y, LOG_Z, LEAVES, ROOTED_DIRT, HANGING_ROOTS }

    public static final int PASS = 0, SOIL = 1, BLOCKED = 2, LOG = 3;

    public interface World {
        /** {@link #PASS} 空气/可替换/树叶，{@link #SOIL} 泥土类，{@link #LOG} 肥沃原木，其余为 {@link #BLOCKED}。 */
        int probe(int x, int y, int z);

        /** 写入方块；主干等必需部分被挡住时由实现抛出异常，使整次生长回滚。 */
        void put(int x, int y, int z, Kind kind);
    }

    /** 气根落点：x、z 与地面高度 y（落点方块本身是泥土，树干从 y+1 开始）。 */
    public record Pillar(int x, int y, int z) {}

    private static final int MAX_BRANCHES = 9;

    private final World world;
    private final int tier;
    private final int radiusCap;
    private final Random random;
    private final List<Branch> branches = new ArrayList<>();
    private final List<int[]> path = new ArrayList<>();
    private final Set<Long> placedLogs = new HashSet<>();

    private record Branch(double angle, int height, double length, boolean upper) {}

    private BanyanShape(World world, int tier, long seed, int radiusCap) {
        this.world = world;
        this.tier = tier;
        this.radiusCap = radiusCap;
        this.random = new Random(seed);
    }

    /**
     * 按等级生长一棵榕树。
     *
     * @param pillars 已有气根落点；为 null 表示首次成林
     * @param wanted  首次成林需要的气根数量（不含主干）；升级时忽略
     * @return 全部气根落点（已有的在前）。首次成林时数量不足说明地形不允许，调用方应放弃本次生长；
     *         升级时在新长出的外圈枝下追加气根（2 级 +6、3 级 +10），越长越成林
     */
    public static List<Pillar> grow(World world, int tier, long seed, int radiusCap, List<Pillar> pillars, int wanted) {
        return new BanyanShape(world, tier, seed, radiusCap).run(pillars, wanted);
    }

    private static int trunkHeight(int tier) { return tier == 1 ? 13 : tier == 2 ? 15 : 18; }
    private static int rootDepth(int tier) { return tier == 1 ? 2 : tier == 2 ? 4 : 8; }
    private double reach() { return reach(tier); }
    private double reach(int t) { return Math.min(radiusCap, t == 1 ? 9 : t == 2 ? 12 : 15); }
    private static int extraPillars(int tier) { return tier == 2 ? 6 : tier == 3 ? 10 : 0; }

    private List<Pillar> run(List<Pillar> existing, int wanted) {
        // 先把所有枝条参数一次抽完，抽取顺序与等级无关，保证不同等级的枝条路径一致
        for (int i = 0; i < MAX_BRANCHES; i++) {
            double angle = (Math.PI * 2 * i) / MAX_BRANCHES + (random.nextDouble() - 0.5) * 0.5;
            int height = 8 + random.nextInt(3);
            double scale = 0.8 + random.nextDouble() * 0.2;
            boolean upper = i >= 7;
            branches.add(new Branch(angle, upper ? height + 3 : height, scale, upper));
        }
        int count = tier == 1 ? 6 : tier == 2 ? 7 : 9;
        long decoSeed = random.nextLong();

        trunk();
        for (int i = 0; i < count; i++) branch(branches.get(i));
        List<Pillar> pillars = new ArrayList<>();
        if (existing == null) pillars.addAll(choosePillars(wanted, pillars, 35));
        else {
            pillars.addAll(existing);
            // 只在上一级树冠之外新长出的枝段下落新气根
            pillars.addAll(choosePillars(extraPillars(tier), pillars, (int) (reach(tier - 1) * 7.5)));
        }
        Map<Long, Integer> floor = branchFloor();
        for (Pillar p : pillars) pillar(p, floor);
        hangingRoots(new Random(decoSeed ^ tier), pillars);
        canopy();
        return pillars;
    }

    // ---------- 主干 ----------
    private boolean inTrunk(int x, int y, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        if (tier == 1) return ax + az <= 1;
        if (tier == 2) return ax <= 1 && az <= 1;
        return ax <= 2 && az <= 2 && (y <= 2 || ax + az < 4);
    }

    private void trunk() {
        int h = trunkHeight(tier);
        for (int y = 0; y < h; y++)
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                if (inTrunk(x, y, z)) log(x, y, z, Kind.LOG_Y, true);
        // 板根：主干底部向八个方向外扩一格，3 级再高一格
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int reachOut = tier == 1 ? 2 : tier == 2 ? 2 : 3;
        for (int[] d : dirs) {
            int x = d[0] * reachOut, z = d[1] * reachOut;
            if (d[0] != 0 && d[1] != 0) { x = d[0] * (reachOut - 1); z = d[1] * (reachOut - 1); }
            if (inTrunk(x, 0, z)) continue;
            if (world.probe(x, 0, z) == PASS && world.probe(x, -1, z) == SOIL) {
                log(x, 0, z, Kind.LOG_Y, false);
                if (tier == 3 && (d[0] == 0 || d[1] == 0) && world.probe(x, 1, z) == PASS) log(x, 1, z, Kind.LOG_Y, false);
            }
        }
        // 根系：主干正下方向下扎，遇到非泥土即停
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (!inTrunk(x, 0, z)) continue;
            for (int d = 1; d <= rootDepth(tier); d++) {
                if (world.probe(x, -d, z) != SOIL) break;
                world.put(x, -d, z, Kind.ROOTED_DIRT);
            }
        }
    }

    // ---------- 大枝 ----------
    /** 枝条沿水平方向伸展，先缓升后放平；遇到障碍就截短，不让整棵树失败。 */
    private void branch(Branch b) {
        double length = reach() * b.length() * (b.upper() ? 0.6 : 1.0);
        int[] tip = limb(b.angle(), b.height(), 0, length, true);
        if (tip == null || b.upper()) return;
        // 两个分叉，分叉点按绝对距离取，不随等级变化
        for (double at : new double[]{5, 8, 11}) {
            if (at > length - 2) break;
            double side = ((int) at % 2 == 0 ? 1 : -1) * 0.6;
            limb(b.angle() + side, b.height() + rise(at), at, Math.min(length - at, 4 + tier), true);
        }
    }

    private static int rise(double d) { return (int) Math.min(3, Math.floor(d / 3.0)); }

    /** 从距主干中心 start 处出发画一段枝，返回末端；记录路径点供气根与树冠使用。 */
    private int[] limb(double angle, int baseY, double start, double length, boolean record) {
        double cx = Math.cos(angle), cz = Math.sin(angle);
        int[] last = null;
        for (double d = 0; d <= length; d += 0.35) {
            double dist = start + d;
            int x = (int) Math.round(cx * dist), z = (int) Math.round(cz * dist);
            int y = baseY + (start == 0 ? rise(dist) : rise(d));
            if (last != null && last[0] == x && last[1] == y && last[2] == z) continue;
            if (inTrunk(x, y, z)) { last = new int[]{x, y, z}; continue; }
            if (last != null && last[1] != y) {
                // 抬升一格时补一个竖向原木，保证枝条连续
                if (!tryLog(last[0], y, last[2], Kind.LOG_Y)) return last;
            }
            Kind axis = Math.abs(cx) >= Math.abs(cz) ? Kind.LOG_X : Kind.LOG_Z;
            if (!tryLog(x, y, z, axis)) return last;
            last = new int[]{x, y, z};
            if (record) path.add(new int[]{x, y, z, (int) Math.round(dist * 10)});
        }
        return last;
    }

    // ---------- 气根树干 ----------
    private List<Pillar> choosePillars(int wanted, List<Pillar> fixed, int minDist10) {
        List<int[]> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] p : path) {
            if (p[3] < minDist10) continue; // 离主干足够远才垂气根（首次成林 3.5 格）
            long key = ((long) p[0] << 32) ^ (p[2] & 0xffffffffL);
            if (!seen.add(key)) continue;
            int ground = groundBelow(p[0], p[1] - 1, p[2]);
            if (ground != Integer.MIN_VALUE && p[1] - ground >= 3) candidates.add(new int[]{p[0], ground, p[2]});
        }
        // 最远点采样：每次取离已选点（含主干）最远的候选，让气根均匀铺开
        List<Pillar> chosen = new ArrayList<>();
        while (chosen.size() < wanted) {
            int[] best = null;
            double bestDist = 0;
            for (int[] c : candidates) {
                double d = c[0] * c[0] + c[2] * c[2] - 4; // 主干算作 2 格半径
                for (Pillar p : fixed) d = Math.min(d, sq(c[0] - p.x()) + sq(c[2] - p.z()));
                for (Pillar p : chosen) d = Math.min(d, sq(c[0] - p.x()) + sq(c[2] - p.z()));
                if (d > bestDist) { bestDist = d; best = c; }
            }
            if (best == null || bestDist < 4) break; // 气根之间至少隔 2 格
            chosen.add(new Pillar(best[0], best[1], best[2]));
        }
        return chosen;
    }

    private static int sq(int v) { return v * v; }

    /** 从 y 往下找落脚的泥土，穿过空气、树叶和本树原木；碰到其他东西返回 MIN_VALUE。 */
    private int groundBelow(int x, int y, int z) {
        for (int d = 0; d < 28; d++) {
            int kind = world.probe(x, y - d, z);
            if (kind == SOIL) return y - d;
            if (kind == BLOCKED) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** 每一列上最低的枝条高度；气根只长到这里，接上枝条。 */
    private Map<Long, Integer> branchFloor() {
        Map<Long, Integer> floor = new HashMap<>();
        for (int[] q : path) floor.merge(key(q[0], 0, q[2]), q[1], Math::min);
        return floor;
    }

    private void pillar(Pillar p, Map<Long, Integer> floor) {
        Integer top = floor.get(key(p.x(), 0, p.z()));
        if (top == null || top - p.y() < 2) return; // 这一列上方已没有枝（被截短），不长悬空的柱子
        if (world.probe(p.x(), p.y(), p.z()) == SOIL) world.put(p.x(), p.y(), p.z(), Kind.ROOTED_DIRT);
        for (int y = p.y() + 1; y < top; y++) if (!tryLog(p.x(), y, p.z(), Kind.LOG_Y)) break;
    }

    /** 还没落地的短气根：1~3 格原木垂下，末端挂一截原版垂根。 */
    private void hangingRoots(Random r, List<Pillar> pillars) {
        int count = tier == 1 ? 4 : tier == 2 ? 8 : 14;
        for (int i = 0, tries = 0; i < count && tries < count * 6 && !path.isEmpty(); tries++) {
            int[] p = path.get(r.nextInt(path.size()));
            if (p[3] < 25) continue;
            boolean nearPillar = false;
            for (Pillar q : pillars) if (Math.abs(q.x() - p[0]) <= 1 && Math.abs(q.z() - p[2]) <= 1) nearPillar = true;
            if (nearPillar || world.probe(p[0], p[1] - 1, p[2]) != PASS) continue;
            int len = 1 + r.nextInt(3), y = p[1] - 1;
            for (int k = 0; k < len && world.probe(p[0], y, p[2]) == PASS && world.probe(p[0], y - 1, p[2]) == PASS; k++, y--)
                tryLog(p[0], y, p[2], Kind.LOG_Y);
            if (world.probe(p[0], y, p[2]) == PASS) world.put(p[0], y, p[2], Kind.HANGING_ROOTS);
            i++;
        }
    }

    // ---------- 树冠 ----------
    /** 沿枝条外段铺扁椭球叶团，主干顶再盖一个穹顶；边缘按哈希随机缺口，避免像整块方糖。 */
    private void canopy() {
        double rx = tier == 1 ? 3.2 : tier == 2 ? 3.7 : 4.3;
        Map<Long, int[]> done = new HashMap<>();
        int i = 0;
        for (int[] p : path) {
            if (p[3] < 30 || (i++ % 4) != 0) continue;
            blob(p[0], p[1] + 1, p[2], rx, 2.2, done, p[1] + 1);
        }
        int h = trunkHeight(tier);
        blob(0, h, 0, rx + 1.5, 2.6, done);
        blob(0, h - 2, 0, rx + 2.5, 1.8, done);
        // 原版树叶离原木超过 6 格会凋落：从本树原木出发沿树叶候选做 6 步广度搜索
        Map<Long, Integer> dist = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (long k : placedLogs) queue.add(unkey(k));
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int d = c.length > 3 ? c[3] : 0;
            if (d >= 6) continue;
            for (int[] st : steps) {
                long k = key(c[0] + st[0], c[1] + st[1], c[2] + st[2]);
                if (!done.containsKey(k) || dist.containsKey(k)) continue;
                dist.put(k, d + 1);
                queue.add(new int[]{c[0] + st[0], c[1] + st[1], c[2] + st[2], d + 1});
            }
        }
        for (Map.Entry<Long, int[]> e : done.entrySet())
            if (dist.containsKey(e.getKey())) world.put(e.getValue()[0], e.getValue()[1], e.getValue()[2], Kind.LEAVES);
    }

    private void blob(int cx, int cy, int cz, double rx, double ry, Map<Long, int[]> done) { blob(cx, cy, cz, rx, ry, done, cy - 1); }

    private void blob(int cx, int cy, int cz, double rx, double ry, Map<Long, int[]> done, int floorY) {
        int r = (int) Math.ceil(rx), ryi = (int) Math.ceil(ry);
        for (int y = -ryi; y <= ryi; y++) for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            if (cy + y < floorY) continue;
            double v = (x * x + z * z) / (rx * rx) + (y * y) / (ry * ry);
            if (v > 1) continue;
            int wx = cx + x, wy = cy + y, wz = cz + z;
            if (v > 0.7 && edgeHash(wx, wy, wz) < 0.35) continue;
            long k = key(wx, wy, wz);
            if (done.containsKey(k) || placedLogs.contains(k)) continue;
            if (world.probe(wx, wy, wz) == PASS) done.put(k, new int[]{wx, wy, wz});
        }
    }

    private static double edgeHash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7fffffff) / 2147483647.0;
    }

    // ---------- 原木 ----------
    private static long key(int x, int y, int z) { return ((long) (x & 0x1fffff) << 42) | ((long) (y & 0x1fffff) << 21) | (z & 0x1fffff); }

    private static int[] unkey(long k) {
        return new int[]{signed((int) (k >>> 42) & 0x1fffff), signed((int) (k >>> 21) & 0x1fffff), signed((int) k & 0x1fffff)};
    }

    private static int signed(int v) { return v >= 0x100000 ? v - 0x200000 : v; }

    /** 主干用：必须放下，挡住就由 World 抛异常回滚整次生长。 */
    private void log(int x, int y, int z, Kind axis, boolean required) {
        if (placedLogs.contains(key(x, y, z))) return;
        if (!required && world.probe(x, y, z) == BLOCKED) return;
        world.put(x, y, z, axis);
        placedLogs.add(key(x, y, z));
    }

    /** 枝条与气根用：挡住就返回 false，由调用方截短。 */
    private boolean tryLog(int x, int y, int z, Kind axis) {
        if (placedLogs.contains(key(x, y, z))) return true;
        int kind = world.probe(x, y, z);
        if (kind == BLOCKED || kind == SOIL) return false;
        world.put(x, y, z, axis);
        placedLogs.add(key(x, y, z));
        return true;
    }
}
