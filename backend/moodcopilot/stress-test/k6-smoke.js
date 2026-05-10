// k6-smoke.js —— 低负载验证所有接口正常
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  iterations: 1,
};

const BASE = 'http://localhost:18080/api';

export default function () {
  // 1. 健康检查
  let res = http.get(`${BASE}/health`);
  check(res, { 'health OK': (r) => r.status === 200 });

  // 2. 登录
  res = http.post(`${BASE}/auth/login`, JSON.stringify({
    email: 'test@test.com', password: '123456',
  }), { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login OK': (r) => r.status === 200 });
  const token = res.json().data.token;

  const auth = { headers: { 'Authorization': `Bearer ${token}` } };

  // 3. 公开日记流
  res = http.get(`${BASE}/diaries/public?page=1&size=20`, auth);
  check(res, { 'public OK': (r) => r.status === 200 });

  // 4. 我的日记
  res = http.get(`${BASE}/diaries/mine?page=1&size=20`, auth);
  check(res, { 'mine OK': (r) => r.status === 200 });

  // 5. 每日状态
  res = http.get(`${BASE}/diaries/today-status`, auth);
  check(res, { 'today-status OK': (r) => r.status === 200 });

  // 6. 今日同频
  res = http.get(`${BASE}/diaries/today-match`, auth);
  check(res, { 'today-match OK': (r) => r.status === 200 });

  // 7. 社区情绪
  res = http.get(`${BASE}/diaries/community-mood`, auth);
  check(res, { 'community-mood OK': (r) => r.status === 200 });

  // 8. 通知
  res = http.get(`${BASE}/notifications`, auth);
  check(res, { 'notifications OK': (r) => r.status === 200 });

  console.log('Smoke test passed!');
}
