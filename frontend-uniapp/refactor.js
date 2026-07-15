const fs = require('fs');
const path = require('path');

const pagesDir = 'd:/Code/MoodCopilot/frontend-uniapp/src/pages';
const files = [];

function findVueFiles(dir) {
  const items = fs.readdirSync(dir);
  for (const item of items) {
    const fullPath = path.join(dir, item);
    if (fs.statSync(fullPath).isDirectory()) {
      findVueFiles(fullPath);
    } else if (fullPath.endsWith('.vue')) {
      files.push(fullPath);
    }
  }
}
findVueFiles(pagesDir);

for (const file of files) {
  let content = fs.readFileSync(file, 'utf8');

  // 1. Replace template styles
  content = content.replace(/:style="localThemeStyle"/g, ':style="globalThemeStyle"');
  content = content.replace(/:style="themeStyle"/g, ':style="globalThemeStyle"');

  // 2. Remove localThemeStyle declaration
  content = content.replace(/const localThemeStyle = ref\(themeStyle\.value\);\n?/g, '');

  // 3. Remove lines inside onShow
  content = content.replace(/localThemeStyle\.value = themeStyle\.value;\n?/g, '');
  content = content.replace(/syncNavigationBarColor\(\);\n?/g, '');

  // 4. Clean up empty onShow
  content = content.replace(/onShow\(\(\) => \{\s*\}\);\n?/g, '');

  // 5. Clean up imports from @/stores/theme
  content = content.replace(/syncNavigationBarColor,?\s*/g, '');
  // themeStyle might still be used if some page watches it manually, let's check
  // if not used, remove it. Actually let's just blindly remove it from imports, and if there's a build error we fix it.
  content = content.replace(/themeStyle,?\s*/g, '');
  
  // Clean up empty imports like import { } from '@/stores/theme'
  content = content.replace(/import\s*\{\s*\}\s*from\s*['"]@\/stores\/theme['"];?\n?/g, '');
  content = content.replace(/import\s*\{\s*,\s*\}\s*from\s*['"]@\/stores\/theme['"];?\n?/g, '');
  content = content.replace(/,\s*\}/g, ' }'); // clean up dangling commas

  // Clean up unused onShow import
  if (!content.includes('onShow(')) {
    content = content.replace(/onShow,?\s*/g, '');
    content = content.replace(/import\s*\{\s*\}\s*from\s*['"]@dcloudio\/uni-app['"];?\n?/g, '');
    content = content.replace(/,\s*\}/g, ' }');
  }

  fs.writeFileSync(file, content, 'utf8');
}

console.log('Refactoring complete');
