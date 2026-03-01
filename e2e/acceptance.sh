#!/bin/bash
set -e
export PATH=$PATH:/home/yu/android-sdk/platform-tools
PKG="com.dmwork.im"
DEVICE=$(adb devices | grep device$ | head -1 | awk '{print $1}')
[ -z "$DEVICE" ] && echo "❌ 无设备" && exit 1
echo "📱 $DEVICE"
adb -s $DEVICE install -r "${1}" || { echo "❌ 安装失败"; exit 1; }
echo "✅ 安装成功"
adb -s $DEVICE shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 && sleep 3
adb -s $DEVICE shell screencap -p /sdcard/dmwork_test.png
adb -s $DEVICE pull /sdcard/dmwork_test.png /tmp/dmwork_android_test.png 2>/dev/null
echo "📸 截图完成"
CRASH=$(adb -s $DEVICE logcat -d -s AndroidRuntime:E | grep "$PKG" | tail -5)
[ -n "$CRASH" ] && echo "❌ Crash: $CRASH" && exit 1
echo "🎉 Android 验收通过!"
