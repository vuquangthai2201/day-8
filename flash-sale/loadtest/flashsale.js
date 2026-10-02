// k6 run loadtest/flashsale.js
// Trước khi chạy: curl -X POST localhost:8080/admin/products/1/stock -H 'Content-Type: application/json' -d '{"stock":1000}'
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_H = { 'Content-Type': 'application/json' };

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));

export const options = {
  scenarios: {
    flash: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 2000 },
        { duration: '60s', target: 5000 }, // tăng dần / chạy phân tán nhiều máy để tiến tới 50k
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_duration{name:stock}': ['p(95)<80'],
    'http_req_duration{name:cart}': ['p(95)<120'],
    'http_req_duration{name:checkout}': ['p(95)<120'],
    'http_req_failed': ['rate<0.01'],
  },
};

export default function () {
  const user = `u-${__VU}-${__ITER}`;
  const h = { headers: { ...JSON_H, 'X-User-Id': user } };

  const stock = http.get(`${BASE}/products/1/stock`, { tags: { name: 'stock' } });
  check(stock, { 'stock 200': (r) => r.status === 200 });

  const cart = http.post(`${BASE}/cart/items`, JSON.stringify({ productId: 1, qty: 1 }),
    { ...h, tags: { name: 'cart' } });

  if (cart.status === 201) {
    const co = http.post(`${BASE}/checkout`, JSON.stringify({ productId: 1 }),
      { headers: { ...h.headers, 'Idempotency-Key': `k-${user}` }, tags: { name: 'checkout' } });
    check(co, { 'checkout accepted': (r) => r.status === 202 || r.status === 200 });
  }
  sleep(Math.random() * 2);
}
