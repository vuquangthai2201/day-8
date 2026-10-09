#!/usr/bin/env bash
# Tóm tắt log GC (định dạng -Xlog:gc* với decorator time,uptime,level,tags).
# Cách dùng: ./scripts/gc-summary.sh [logs/gc.log]
LOG=${1:-logs/gc.log}
[ -f "$LOG" ] || { echo "Không thấy file $LOG"; exit 1; }

awk '
/\[gc *\] GC\([0-9]+\) Pause/ && /ms$/ {
    n++
    ms = $NF; sub(/ms$/, "", ms); ms += 0
    total += ms
    if (ms > max) max = ms
    if ($0 ~ /Humongous Allocation/) hum++
    for (i = 1; i <= NF; i++) {
        if ($i ~ /^[0-9]+M->[0-9]+M\(/) {
            split($i, a, "->"); before = a[1] + 0
            split(a[2], b, "M"); after = b[1] + 0
            if (after > maxafter) maxafter = after
            if (minafter == "" || after < minafter) minafter = after
            if (before > maxbefore) maxbefore = before
        }
    }
    if (first == "") { match($0, /\[[0-9.]+s\]/); first = substr($0, RSTART + 1, RLENGTH - 3) + 0 }
    match($0, /\[[0-9.]+s\]/); last = substr($0, RSTART + 1, RLENGTH - 3) + 0
}
END {
    if (n == 0) { print "Không có lần GC pause nào trong log (đây là dấu hiệu tốt nếu bạn đang đo bản đã sửa)."; exit }
    span = last - first
    printf "Tổng số lần GC pause      : %d\n", n
    printf "  - do Humongous Allocation: %d\n", hum
    printf "Tổng thời gian pause       : %.1f ms\n", total
    printf "Pause dài nhất             : %.2f ms\n", max
    printf "Heap trước GC (đỉnh)       : %d MB\n", maxbefore
    printf "Heap sau GC (min / max)    : %d MB / %d MB   <- không tăng dần = KHÔNG memory leak\n", minafter, maxafter
    if (span > 0) printf "Khoảng thời gian / tần suất: %.2fs -> %.2fs (~%.0f GC/giây)\n", first, last, n / span
}' "$LOG"
