// k6-stress.js —— 高负载找瓶颈
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 30 },
    { duration: '3m', target: 50 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<3000'],
    'http_req_failed': ['rate<0.10'],
  },
};

const BASE = 'http://localhost:18080/api';

export default function () {
  const vuId = __VU % 10;
  const email = `loadtest${vuId}@test.com`;

  const login = http.post(`${BASE}/auth/login`, JSON.stringify({
    email: email, password: '123456',
  }), { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200) { sleep(1); return; }
  const token = login.json().data.token;
  const auth = { headers: { 'Authorization': `Bearer ${token}` } };

  http.get(`${BASE}/diaries/public?page=1&size=20`, auth);
  http.get(`${BASE}/diaries/public?page=2&size=20`, auth);
  http.get(`${BASE}/diaries/mine?page=1&size=20`, auth);
  http.get(`${BASE}/diaries/today-status`, auth);
  http.get(`${BASE}/diaries/today-match`, auth);
  http.get(`${BASE}/notifications`, auth);
  http.get(`${BASE}/notifications/unread-count`, auth);

  sleep(1);
}
