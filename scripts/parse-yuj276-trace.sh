#!/usr/bin/env bash
# YUJ-278 · 从 adb logcat dump（tag=YUJ276-trace / YUJ278-transition）中还原
# 每次「点击 → onResume」的 breakdown，输出 P50/P90。
#
# 用法：bash scripts/parse-yuj276-trace.sh <logcat.log>
# 依赖：awk（BSD / GNU 均可）、sort、bc（可选，用于百分位计算）。
set -euo pipefail

LOG="${1:-/dev/stdin}"
[[ -r "$LOG" ]] || { echo "log file not readable: $LOG" >&2; exit 1; }

# 用 awk 把每次 startChat 的 5 个阶段时间戳按 channel 聚合为一条记录。
# 输出每个阶段对一系列样本，然后 sort + 百分位。
awk '
function record(stage, ms) {
    samples[stage, ++n[stage]] = ms + 0
}
/T_CLICK\]/            { split($0, a, "channel="); ch=a[2]; sub(/ .*/,"",ch); t_click[ch]=systime_ms(); record("__click",0); next }
/T_INTENT_BUILT\]/     { if (match($0, /sinceClick=([0-9]+)ms/, m)) record("click_intent", m[1]); next }
/T_START_ACTIVITY\]/   { if (match($0, /sinceClick=([0-9]+)ms/, m)) record("click_start",  m[1]); next }
/T_ON_CREATE_END\]/    { if (match($0, /total=([0-9]+)ms/,      m)) record("oncreate_total",m[1]); next }
/T_ON_START_END\]/     { if (match($0, /total=([0-9]+)ms/,      m)) record("onstart_total", m[1]); next }
/T_ON_RESUME_END\]/    { if (match($0, /total=([0-9]+)ms/,      m)) record("onresume_tail", m[1]); next }
/T_ON_NEW_INTENT_END\]/{ if (match($0, /total=([0-9]+)ms/,      m)) record("newintent_tot", m[1]); next }

function percentile(arr, nn, p,   i, vals, v) {
    for (i=1;i<=nn;i++) vals[i]=arr[i]
    # simple insertion sort (small N)
    for (i=2;i<=nn;i++) { v=vals[i]; j=i-1
        while (j>=1 && vals[j]>v) { vals[j+1]=vals[j]; j-- }
        vals[j+1]=v }
    idx=int(p*nn+0.5); if (idx<1) idx=1; if (idx>nn) idx=nn
    return vals[idx]
}

END {
    fmt = "%-22s %-8s %-8s %-6s\n"
    printf fmt, "stage", "p50", "p90", "N"
    print  "---"
    for (stage in n) {
        if (stage == "__click") continue
        local_samples[0]=""; delete local_samples
        for (i=1;i<=n[stage];i++) local_samples[i]=samples[stage,i]+0
        p50 = percentile(local_samples, n[stage], 0.5)
        p90 = percentile(local_samples, n[stage], 0.9)
        printf fmt, stage, p50"ms", p90"ms", n[stage]
    }
}
' "$LOG"
