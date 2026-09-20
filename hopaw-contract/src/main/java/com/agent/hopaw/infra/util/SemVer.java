package com.agent.hopaw.infra.util;

/**
 * 语义化版本比较（SemVer 2.0 的核心比较规则）。
 *
 * <p>用于插件仓库与商店的版本排序、以及"是否有新版本"的判定。
 * 直接字符串比较会把 {@code 1.10.0} 判成小于 {@code 1.9.0}，必须走本工具。</p>
 *
 * <p>规则要点：</p>
 * <ul>
 *   <li>忽略 build 元数据（{@code +} 之后的部分）；</li>
 *   <li>主版本号按 {@code .} 分段做数值比较，缺失段视为 0（{@code 1.2} == {@code 1.2.0}）；</li>
 *   <li>存在预发布标识（{@code -} 之后）的版本小于同主版本的正式版；</li>
 *   <li>预发布标识逐个比较：纯数字段按数值比较且小于非数字段，非数字段按字典序比较。</li>
 * </ul>
 *
 * <p>非法/空版本号按 {@code 0.0.0} 处理，保证不会抛异常。</p>
 */
public final class SemVer {

    private SemVer() {
    }

    /** 比较两个版本号：a &gt; b 返回正数，a &lt; b 返回负数，相等返回 0。 */
    public static int compare(String a, String b) {
        String va = normalize(a);
        String vb = normalize(b);
        if (va.equals(vb)) {
            return 0;
        }

        String coreA = core(va);
        String preA = preRelease(va);
        String coreB = core(vb);
        String preB = preRelease(vb);

        int coreCmp = compareCore(coreA, coreB);
        if (coreCmp != 0) {
            return coreCmp;
        }
        return comparePreRelease(preA, preB);
    }

    /** candidate 是否比 base 更新（严格大于）。 */
    public static boolean isNewer(String candidate, String base) {
        return compare(candidate, base) > 0;
    }

    /** 返回两者中较新的版本号；用于"取最新版本"场景。 */
    public static String max(String a, String b) {
        return compare(a, b) >= 0 ? a : b;
    }

    // ==================== 内部实现 ====================

    private static String normalize(String version) {
        if (version == null) {
            return "0.0.0";
        }
        String v = version.trim();
        return v.isEmpty() ? "0.0.0" : v;
    }

    /** 去掉 build 元数据（{@code +} 之后）。 */
    private static String stripBuild(String version) {
        int idx = version.indexOf('+');
        return idx < 0 ? version : version.substring(0, idx);
    }

    private static String core(String version) {
        String v = stripBuild(version);
        int idx = v.indexOf('-');
        return idx < 0 ? v : v.substring(0, idx);
    }

    private static String preRelease(String version) {
        String v = stripBuild(version);
        int idx = v.indexOf('-');
        return idx < 0 ? null : v.substring(idx + 1);
    }

    private static int compareCore(String coreA, String coreB) {
        String[] sa = coreA.split("\\.", -1);
        String[] sb = coreB.split("\\.", -1);
        int len = Math.max(sa.length, sb.length);
        for (int i = 0; i < len; i++) {
            String x = i < sa.length ? sa[i] : "0";
            String y = i < sb.length ? sb[i] : "0";
            if (x.equals(y)) {
                continue;
            }
            Integer nx = parseIntOrNull(x);
            Integer ny = parseIntOrNull(y);
            if (nx != null && ny != null) {
                int cmp = Integer.compare(nx, ny);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                // 非纯数字段（如 1.0.x）：退化为字典序，但仍保证稳定
                int cmp = x.compareTo(y);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }
        return 0;
    }

    private static int comparePreRelease(String preA, String preB) {
        if (preA == null && preB == null) {
            return 0;
        }
        // 正式版 > 预发布版
        if (preA == null) {
            return 1;
        }
        if (preB == null) {
            return -1;
        }

        String[] ia = preA.split("\\.", -1);
        String[] ib = preB.split("\\.", -1);
        int len = Math.max(ia.length, ib.length);
        for (int i = 0; i < len; i++) {
            if (i >= ia.length) {
                return -1; // 前缀相同，标识少的更小
            }
            if (i >= ib.length) {
                return 1;
            }
            String x = ia[i];
            String y = ib[i];
            if (x.equals(y)) {
                continue;
            }
            Integer nx = parseIntOrNull(x);
            Integer ny = parseIntOrNull(y);
            if (nx != null && ny != null) {
                return Integer.compare(nx, ny);
            }
            if (nx != null) {
                return -1; // 数字标识 < 非数字标识
            }
            if (ny != null) {
                return 1;
            }
            return x.compareTo(y);
        }
        return 0;
    }

    private static Integer parseIntOrNull(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
