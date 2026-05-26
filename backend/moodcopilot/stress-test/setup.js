// setup.js —— 在压测前创建测试数据
import http from 'k6/http';
import { check } from 'k6';

const BASE = 'http://localhost:18080/api';

export function setup() {
  const users = [];
  for (let i = 0; i < 10; i++) {
    const email = `loadtest${i}@test.com`;
    const password = '123456';
    let token = null;

    // 先尝试注册（用户可能已存在，忽略失败）
    const regRes = http.post(`${BASE}/auth/register`, JSON.stringify({
      displayName: `Tester${i}`,
      email: email,
      password: password,
    }), { headers: { 'Content-Type': 'application/json' } });

    if (regRes.status === 200 && regRes.body) {
      try {
        const regData = JSON.parse(regRes.body);
        token = regData.data && regData.data.token;
      } catch (e) {}
    }

    // 如果注册没拿到 token，尝试登录
    if (!token) {
      const loginRes = http.post(`${BASE}/auth/login`, JSON.stringify({
        email: email,
        password: password,
      }), { headers: { 'Content-Type': 'application/json' } });

      if (loginRes.status === 200 && loginRes.body) {
        try {
          const loginData = JSON.parse(loginRes.body);
          token = loginData.data && loginData.data.token;
        } catch (e) {}
      }
    }

    if (token) {
      const moods = ['今天很开心', '有点累但还好', '遇到了一些挫折', '平静的一天', '期待明天'];
      for (let j = 0; j < 5; j++) {
        http.post(`${BASE}/diaries`, JSON.stringify({
          content: moods[j] + ` - 压测用户${i}的第${j + 1}篇`,
          visibility: j % 2 === 0 ? 'PUBLIC' : 'PRIVATE',
        }), { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } });
      }
      console.log(`User ${i} ready: ${email}`);
    } else {
      console.log(`FAILED to get token for user ${i}: ${email}`);
    }
    users.push({ email, token });
  }
  return { users };
}

export default function () { }
