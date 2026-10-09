#!/usr/bin/env bash
# Gửi tải tới một endpoint.
# Cách dùng: ./scripts/load.sh <đường-dẫn> <số-request> <số-luồng-đồng-thời>
# Ví dụ:
#   ./scripts/load.sh /process 5 1          # 5 request tuần tự tới bản LỖI
#   ./scripts/load.sh /process-fixed 100 1  # 100 request tuần tự tới bản ĐÃ SỬA
#   ./scripts/load.sh /process 6 3          # 6 request, 3 luồng đồng thời (thấy CPU/GC bão hòa)
HOST=${HOST:-http://localhost:8080}
PATH_URL=${1:-/process}
COUNT=${2:-5}
CONC=${3:-1}

echo ">> $COUNT request tới $HOST$PATH_URL (đồng thời: $CONC)"
seq "$COUNT" | xargs -P "$CONC" -I{} curl -s -o /dev/null \
  -w "request {} -> HTTP %{http_code} trong %{time_total}s\n" "$HOST$PATH_URL"
echo ">> Xong."
