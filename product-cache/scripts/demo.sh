#!/usr/bin/env bash
# Kịch bản demo: cần 2 instance đang chạy (8080 = A, 8081 = B) và Redis/Postgres đã up.
# Quan sát log ở hai terminal của A và B để thấy [L1], [DB], [PUB], [SUB].
A=${A:-http://localhost:8080}
B=${B:-http://localhost:8081}
JSON='Content-Type: application/json'

step() { echo; echo "=================== $* ==================="; }

step "1. Tạo sản phẩm mới qua instance A"
RESP=$(curl -s -X POST "$A/products" -H "$JSON" -d '{"name":"Demo","description":"San pham demo","price":99.90}')
echo "$RESP"
ID=$(echo "$RESP" | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "-> id = $ID"

step "2. GET lần 1 trên A: L1 miss, L2 miss, đọc DB (xem log [DB])"
curl -s "$A/products/$ID"; echo

step "3. GET lần 2 trên A: L1 HIT"
curl -s "$A/products/$ID"; echo

step "4. GET trên B: L1 miss nhưng L2 HIT (không có log [DB] ở B)"
curl -s "$B/products/$ID"; echo

step "5. Key trong Redis (L2)"
docker exec product-redis redis-cli EXISTS "products::$ID"

step "6. PUT trên A: đổi giá -> L2 ghi đè, L1 của B bị xóa qua product-update"
curl -s -X PUT "$A/products/$ID" -H "$JSON" -d '{"name":"Demo","description":"San pham demo","price":149.90}'; echo
sleep 1
echo "GET trên B (phải thấy giá mới 149.90):"
curl -s "$B/products/$ID"; echo

step "7. DELETE trên A: xóa DB, xóa L2, publish product-deletion"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X DELETE "$A/products/$ID"
sleep 1

step "8. Kiểm tra sau khi xóa"
echo "Key L2 còn tồn tại? (0 = đã bị xóa)"
docker exec product-redis redis-cli EXISTS "products::$ID"
echo "GET trên A:"
curl -s -o /dev/null -w "HTTP %{http_code}\n" "$A/products/$ID"
echo "GET trên B (L1 của B đã bị xóa nhờ Pub/Sub nên phải 404):"
curl -s -o /dev/null -w "HTTP %{http_code}\n" "$B/products/$ID"
