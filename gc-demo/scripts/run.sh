#!/usr/bin/env bash
# Chạy ứng dụng với heap nhỏ + log GC đầy đủ (không xoay vòng, để không mất phần đầu log).
#
# Biến môi trường tuỳ chọn:
#   HEAP=256m          kích thước heap (mặc định 256m)
#   GC=G1|Parallel     bộ thu gom rác (mặc định G1)
#   JAVA_OPTS="..."    cờ JVM bổ sung, ví dụ JAVA_OPTS="-XX:G1HeapRegionSize=4m"
#
# Ví dụ:
#   ./scripts/run.sh
#   GC=Parallel ./scripts/run.sh
#   JAVA_OPTS="-XX:G1HeapRegionSize=4m" ./scripts/run.sh
set -e
cd "$(dirname "$0")/.."

HEAP=${HEAP:-256m}
GC=${GC:-G1}
JAR=target/gc-demo-0.0.1-SNAPSHOT.jar

mkdir -p logs
if [ ! -f "$JAR" ]; then
  echo ">> Chưa có JAR, đang build..."
  mvn -q -DskipTests package
fi

rm -f logs/gc.log logs/gc.log.*
echo ">> Chạy: heap=$HEAP, GC=$GC, extra='$JAVA_OPTS'"
echo ">> Log GC: logs/gc.log"
exec java -Xms"$HEAP" -Xmx"$HEAP" "-XX:+Use${GC}GC" $JAVA_OPTS \
  "-Xlog:gc*:file=logs/gc.log:time,uptime,level,tags:filecount=0" \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/ \
  -jar "$JAR"
