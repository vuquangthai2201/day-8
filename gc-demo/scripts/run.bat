@echo off
REM Chạy ứng dụng với heap nhỏ + log GC (Windows).
REM Ví dụ:  scripts\run.bat            (G1, heap 256m)
REM         scripts\run.bat Parallel   (ParallelGC)
cd /d "%~dp0\.."
set GC=%1
if "%GC%"=="" set GC=G1
if not exist logs mkdir logs
if not exist target\gc-demo-0.0.1-SNAPSHOT.jar call mvn -q -DskipTests package
del /q logs\gc.log 2>nul
echo Chay: heap=256m, GC=%GC%
java -Xms256m -Xmx256m -XX:+Use%GC%GC "-Xlog:gc*:file=logs/gc.log:time,uptime,level,tags:filecount=0" -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/ -jar target\gc-demo-0.0.1-SNAPSHOT.jar
