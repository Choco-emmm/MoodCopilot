const fs = require('fs');
let code = fs.readFileSync('d:/Code/MoodCopilot/frontend/src/components/profile/ProfileSettingsModal.vue', 'utf8');
const lines = code.split('\n');

lines[274] = "  { value: 'starlight-blue', label: '星光蓝', primary: '#41BBC8', bg: '#FCF9E8' },";
lines[275] = "  { value: 'bamboo-moon', label: '竹月色', primary: '#6090B8', bg: '#F3D8C3' },";
lines[276] = "  { value: 'pine-cone', label: '松果褐', primary: '#664B3A', bg: '#DBC6B4' }";
lines[358] = "      uploadMsg.value = '头像已更新'";
lines[543] = "    window..error('提交失败：' + (err.response?.data?.message || err.message))";
lines[558] = "    if (nameMsg.value === '该用户名已被占用' || nameMsg.value === '需为 2-20 位中英文、数字、下划线或横线') nameMsg.value = ''";
lines[562] = "    nameMsg.value = '需为 2-20 位中英文、数字、下划线或横线'";
lines[570] = "      if (nameMsg.value === '该用户名已被占用' || nameMsg.value === '需为 2-20 位中英文、数字、下划线或横线') nameMsg.value = ''";
lines[573] = "    logWarn('profile', '检查用户名可用性失败', name, e)";
lines[574] = "    nameMsg.value = '需为 2-20 位中英文、数字、下划线或横线'";
lines[581] = "    if (msg && msg !== '该用户名已被占用') nameMsg.value = msg";
lines[668] = "    passwordMsg.value = e?.response?.data?.message || '验证码发送失败'";
lines[676] = "    passwordMsg.value = '请输入当前密码'";
lines[680] = "    passwordMsg.value = '新密码至少 6 位'";
lines[688] = "    passwordMsg.value = '两次输入的新密码不一致'";
lines[693] = "    passwordMsg.value = e?.response?.data?.message || '密码修改失败'";

fs.writeFileSync('d:/Code/MoodCopilot/frontend/src/components/profile/ProfileSettingsModal.vue', lines.join('\n'), 'utf8');
