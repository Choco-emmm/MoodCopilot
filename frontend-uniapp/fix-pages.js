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

  // Inject syncNavigationBarColor into imports
  if (content.includes('import { themeStyle }')) {
    content = content.replace('import { themeStyle }', 'import { themeStyle, syncNavigationBarColor }');
  } else if (!content.includes('syncNavigationBarColor')) {
    // If not imported at all, let's inject it safely
    content = content.replace('<script setup lang="ts">', '<script setup lang="ts">\nimport { themeStyle, syncNavigationBarColor } from \'@/stores/theme\';');
  }
  
  // Ensure onShow is imported
  if (content.includes('import { onLoad } from \'@dcloudio/uni-app\'')) {
    content = content.replace('import { onLoad } from \'@dcloudio/uni-app\'', 'import { onLoad, onShow } from \'@dcloudio/uni-app\'');
  } else if (!content.includes('onShow') && content.includes('@dcloudio/uni-app')) {
    content = content.replace('from \'@dcloudio/uni-app\'', ', onShow } from \'@dcloudio/uni-app\'').replace('{ ,', '{ ');
  } else if (!content.includes('@dcloudio/uni-app')) {
    content = content.replace('<script setup lang="ts">', '<script setup lang="ts">\nimport { onShow } from \'@dcloudio/uni-app\';');
  }

  // Inject onShow hook
  if (!content.includes('onShow(() => {')) {
    content = content.replace('</script>', '\nonShow(() => {\n  syncNavigationBarColor();\n});\n</script>');
  }

  fs.writeFileSync(file, content, 'utf8');
}
console.log('Fixed missing pages');
