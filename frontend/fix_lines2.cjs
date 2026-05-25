const fs = require('fs');
let code = fs.readFileSync('d:/Code/MoodCopilot/frontend/src/components/profile/ProfileSettingsModal.vue', 'utf8');
const lines = code.split('\n');

// submitSuggestion error message
lines[328] = "    window..error('提交失败：' + (err.response?.data?.message || err.message))";

// upload avatar message
lines[543] = "      uploadMsg.value = '头像已更新'";

fs.writeFileSync('d:/Code/MoodCopilot/frontend/src/components/profile/ProfileSettingsModal.vue', lines.join('\n'), 'utf8');
