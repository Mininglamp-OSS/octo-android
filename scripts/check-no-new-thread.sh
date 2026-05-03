#!/usr/bin/env bash
# YUJ-283 P-11 · 仓内检查：禁止应用代码直接写 `new Thread(...)`。
#
# 范围：wkbase / wkuikit / wkpush / wkscan。所有场景应走
# com.chat.base.utils.AppExecutors（io / background / db）或
# com.chat.base.utils.WKDbScheduler（Rx 场景）。
#
# 例外（白名单）：
#   - wkim/                      —— WKIM SDK 自带 maven publish 链路，独立治理。
#   - wkbase/.../AppExecutors.java —— 自身内部 ThreadFactory。
#   - wkbase/.../WKDbScheduler.java —— Rx 单线程 DB ThreadFactory。
#   - wkbase/.../CrashHandler.java —— Crash 路径需要独立 Looper 线程（主 Looper 即将退出）。
#
# 退出码：
#   0 = 通过；非 0 = 发现违规，打印违规行。

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

INCLUDE_DIRS=(wkbase wkuikit wkpush wkscan app)
# 允许出现 `new Thread` 的文件（相对于仓库根）。
ALLOW_FILES=(
  "wkbase/src/main/java/com/chat/base/utils/AppExecutors.java"
  "wkbase/src/main/java/com/chat/base/utils/WKDbScheduler.java"
  "wkbase/src/main/java/com/chat/base/utils/CrashHandler.java"
)

hits=()
for d in "${INCLUDE_DIRS[@]}"; do
  [[ -d "$d" ]] || continue
  while IFS= read -r line; do
    file="${line%%:*}"
    # 剔除注释行（//、*、#）与字符串内 match
    text="${line#*:}"
    text="${text#*:}"
    trimmed="$(echo "$text" | sed 's/^[[:space:]]*//')"
    case "$trimmed" in
      '//'*) continue ;;
      '*'*)  continue ;;
      '#'*)  continue ;;
    esac
    skip=0
    for allow in "${ALLOW_FILES[@]}"; do
      [[ "$file" == "$allow" ]] && skip=1 && break
    done
    [[ $skip -eq 1 ]] && continue
    hits+=("$line")
  done < <(grep -rn "new Thread\b" --include="*.java" --include="*.kt" "$d" || true)
done

if [[ ${#hits[@]} -ne 0 ]]; then
  echo "::error::YUJ-283 P-11 违规：发现 $(printf '%s\n' "${hits[@]}" | wc -l | tr -d ' ') 处 \`new Thread\`（请改用 AppExecutors）:" >&2
  printf '  %s\n' "${hits[@]}" >&2
  echo >&2
  echo "参考：wkbase/src/main/java/com/chat/base/utils/AppExecutors.java" >&2
  exit 1
fi

echo "✓ no \`new Thread\` outside whitelist (checked: ${INCLUDE_DIRS[*]})"
