const fs = require('fs');
const files = [
  'd:/Code/MoodCopilot/frontend-uniapp/src/pages/collections/collections.vue',
  'd:/Code/MoodCopilot/frontend-uniapp/src/pages/detail/detail.vue',
  'd:/Code/MoodCopilot/frontend-uniapp/src/pages/feedback/feedback.vue',
  'd:/Code/MoodCopilot/frontend-uniapp/src/pages/growth/growth.vue',
  'd:/Code/MoodCopilot/frontend-uniapp/src/pages/notifications/notifications.vue',
  'd:/Code/MoodCopilot/frontend-uniapp/src/pages/search/search.vue',
  'd:/Code/MoodCopilot/frontend-uniapp/src/pages/settings/settings.vue',
  'd:/Code/MoodCopilot/frontend-uniapp/src/pages/summaries/summaries.vue'
];

for (const file of files) {
  let content = fs.readFileSync(file, 'utf8');
  let lines = content.split('\n');
  let firstViewLine = lines.find(l => l.includes('<view'));
  if (firstViewLine && !firstViewLine.includes(':style=')) {
    console.log('Missing style on root:', file);
  }
}
