// k6-load.js —— 模拟正常用户流量
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '2m',  target: 20 },
    { duration: '2m',  target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<2000'],
    'http_req_failed': ['rate<0.05'],
  },
};

const BASE = 'http://localhost:18080/api';

export default function () {
  const vuId = __VU;
  const email = `loadtest${vuId % 10}@test.com`;

  const login = http.post(`${BASE}/auth/login`, JSON.stringify({
    email: email, password: '123456',
  }), { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200) { sleep(1); return; }
  const token = login.json().data.token;
  const auth = { headers: { 'Authorization': `Bearer ${token}` } };

  const actions = [
    () => http.get(`${BASE}/diaries/public?page=1&size=10`, auth),
    () => http.get(`${BASE}/diaries/mine?page=1&size=10`, auth),
    () => http.get(`${BASE}/diaries/today-status`, auth),
    () => http.get(`${BASE}/diaries/today-match`, auth),
    () => http.get(`${BASE}/notifications`, auth),
  ];

  for (let i = 0; i < 10; i++) {
    const action = actions[Math.floor(Math.random() * actions.length)];
    const res = action();
    check(res, { 'status 200': (r) => r.status === 200 });
    sleep(Math.random() * 2 + 1);
  }
}
