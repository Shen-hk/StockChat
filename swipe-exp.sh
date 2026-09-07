#!/bin/bash
# 左滑导航异常判定实验：滑动前后各记录一次 Activity 栈 + 截图
# 判定逻辑：
#   滑动后栈里 KuiklyRenderActivity 数量减少 → 页面被 finish（系统返回/路由关闭）
#   数量不变但内容变成别的页 → Kuikly 页内换页（路由被意外调用）
#   数量增加 → 触发了 openPage（点击穿透到行点击）
set -e
cd "$(dirname "$0")"

echo "== 1. 设备与进程 =="
adb devices | tail -2
adb shell pidof com.kuikly.stockchat || { echo "应用未运行，先启动"; adb shell monkey -p com.kuikly.stockchat -c android.intent.category.LAUNCHER 1; sleep 6; }

echo "== 2. 当前界面截图（请人工确认在自选页）=="
adb exec-out screencap -p > exp-before.png

echo "== 3. 滑动前栈 =="
adb shell dumpsys activity activities | grep -iE "KuiklyRenderActivity|TaskRecord|*task*" | grep -viE "BarFollow|DeskTop" > stack-before.txt || true
cat stack-before.txt

echo "== 4. 清日志并执行左滑（平安银行行，慢速）=="
adb logcat -c
adb shell input swipe 900 1452 420 1452 700
sleep 1.5

echo "== 5. 滑动后截图与栈 =="
adb exec-out screencap -p > exp-after.png
adb shell dumpsys activity activities | grep -iE "KuiklyRenderActivity|TaskRecord" | grep -viE "BarFollow|DeskTop" > stack-after.txt || true
cat stack-after.txt

echo "== 6. 应用进程日志（手势/异常）=="
PID=$(adb shell pidof com.kuikly.stockchat | tr -d '\r')
adb logcat -d | grep " $PID " | grep -iE "motionevent|KLog|AndroidRuntime|FATAL|KRException|exception" | tail -20 || echo "(无相关日志)"

echo "== 完成：对比 exp-before.png / exp-after.png 与两份栈快照 =="
