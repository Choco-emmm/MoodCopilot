// setup.js —— 在压测前创建测试数据
import http from 'k6/http';
import { check } from 'k6';

const BASE = 'http://localhost:18080/api';

export function setup() {
  const users = [];
  for (let i = 0; i < 10; i++) {
    const email = `loadtest${i}@test.com`;
    const res = http.post(`${BASE}/auth/login`, JSON.stringify({
      email: email,
      password: '123456',
    }), { headers: { 'Content-Type': 'application/json' } });
    let token = null;
    if (res.status === 200) {
      token = res.json().data.token;
    } else {
      const reg = http.post(`${BASE}/auth/register`, JSON.stringify({
        displayName: `Tester${i}`,
        email: email,
        password: '123456',
      }), { headers: { 'Content-Type': 'application/json' } });
      token = reg.json().data.token;
    }
    if (token) {
      const moods = ['今天很开心', '有点累但还好', '遇到了一些挫折', '平静的一天', '期待明天'];
      for (let j = 0; j < 5; j++) {
        http.post(`${BASE}/diaries`, JSON.stringify({
          content: moods[j] + ` - 来自压测用户${i}的第${j + 1}篇日记`,
          visibility: j % 2 === 0 ? 'PUBLIC' : 'PRIVATE',
        }), { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } });
      }
    }
    users.push({ email, token });
  }
  return { users };
}

export default function () { }
