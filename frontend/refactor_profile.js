const fs = require('fs');
const path = 'd:/Code/MoodCopilot/frontend/src/pages/UserProfilePage.vue';
let content = fs.readFileSync(path, 'utf8');

// 1. Replace search panel
const searchPanelStart = '      <!-- 搜索卡片面板 -->\n      <transition name="fade-slide">';
const searchPanelEnd = '        </div>\n      </transition>';
const searchPanelRegex = new RegExp(searchPanelStart.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + searchPanelEnd.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));

content = content.replace(searchPanelRegex, `      <!-- 搜索卡片面板 -->
      <ProfileSearchPanel
        v-if="isOwner"
        :show="showSearchPanel"
        v-model:keyword="keyword"
        v-model:startDate="startDateVal"
        v-model:endDate="endDateVal"
        v-model:visibility="visibilityFilter"
        :visibility-opts="visibilityOpts"
        :date-disabled="dateDisabled"
        :loading="loading"
        @search="triggerSearch"
        @clear="clearFilters"
      />`);

// 2. Replace Modals
const modalsStart = '    <n-modal\n      v-model:show="showSettingsModal"';
const modalsEnd = '    </n-modal>';
const lastModalEndIndex = content.lastIndexOf('    </n-modal>\n  </main>');
const firstModalStartIndex = content.indexOf(modalsStart);

if (firstModalStartIndex !== -1 && lastModalEndIndex !== -1) {
    const replacement = `    <ProfileSettingsModal
      v-model:show="showSettingsModal"
      :is-owner="isOwner"
      @profile-updated="handleProfileUpdated"
      @open-admin-suggestions="showAdminSuggestions = true"
    />
    <AdminSuggestionsModal v-model:show="showAdminSuggestions" />`;
    
    content = content.substring(0, firstModalStartIndex) + replacement + content.substring(lastModalEndIndex + 14); // 14 for '    </n-modal>'
} else {
    console.error('Could not find modals block');
}

// 3. Imports and Setup
// Find imports: import { authApi, diaryApi, memoryApi, suggestionApi } from '../api'
content = content.replace(
  "import { authApi, diaryApi, memoryApi, suggestionApi } from '../api'",
  "import { authApi, diaryApi, memoryApi } from '../api'"
);

// Add component imports after AppHeader
content = content.replace(
  "import AppHeader from '../components/AppHeader.vue'",
  "import AppHeader from '../components/AppHeader.vue'\nimport ProfileSettingsModal from '../components/profile/ProfileSettingsModal.vue'\nimport ProfileSearchPanel from '../components/profile/ProfileSearchPanel.vue'\nimport AdminSuggestionsModal from '../components/profile/AdminSuggestionsModal.vue'"
);

// Add handleProfileUpdated function
const handleProfileUpdatedCode = `\nfunction handleProfileUpdated() {
  if (isOwner.value) {
    profileName.value = auth.displayName || '我'
    profileSignature.value = auth.signature || ''
    profileAvatar.value = auth.avatar || null
  }
}\n`;

// insert handleProfileUpdated before function clearFilters
content = content.replace('function clearFilters() {', handleProfileUpdatedCode + '\nfunction clearFilters() {');

// 4. Remove variables and functions
// fileInput to passwordCodeTimer
const varsToRemove1Start = 'const fileInput = ref<HTMLInputElement | null>(null)';
const varsToRemove1End = 'let passwordCodeTimer: number | null = null';
const varsToRemove1Regex = new RegExp(varsToRemove1Start.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + varsToRemove1End.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\n');
content = content.replace(varsToRemove1Regex, '');

// suggestionContent to submittingSuggestion
const varsToRemove2Start = "const suggestionContent = ref('')";
const varsToRemove2End = "const submittingSuggestion = ref(false)";
const varsToRemove2Regex = new RegExp(varsToRemove2Start.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + varsToRemove2End.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\n');
content = content.replace(varsToRemove2Regex, '');

// adminSuggestions is replaced with just showAdminSuggestions
const varsToRemove3Start = "const adminSuggestions = ref<any[]>([])";
const varsToRemove3End = "const adminSuggestionsLoadingMore = ref(false)";
const varsToRemove3Regex = new RegExp(varsToRemove3Start.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + varsToRemove3End.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\n');
content = content.replace(varsToRemove3Regex, '');

// cropImageSrc to drawRafId
const varsToRemove4Start = "const showCropModal = ref(false)";
const varsToRemove4End = "let drawRafId: number | null = null";
const varsToRemove4Regex = new RegExp(varsToRemove4Start.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + varsToRemove4End.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\n');
content = content.replace(varsToRemove4Regex, 'const showAdminSuggestions = ref(false)\n');

// watch showSettingsModal and showCropModal
const watchToRemoveStart = 'watch(showSettingsModal, (val) => {';
const watchToRemoveEnd = 'setTimeout(() => drawCrop(), 150)\n  }\n})';
const watchToRemoveRegex = new RegExp(watchToRemoveStart.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + watchToRemoveEnd.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\n');
content = content.replace(watchToRemoveRegex, '');

// hydrateSettingsData to loadMoreAdminSuggestions
const funcToRemove1Start = 'async function hydrateSettingsData() {';
const funcToRemove1End = 'adminSuggestionsLoadingMore.value = false\n  }\n}';
const funcToRemove1Regex = new RegExp(funcToRemove1Start.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + funcToRemove1End.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\n');
content = content.replace(funcToRemove1Regex, '');

// triggerUpload to handleLogout
const funcToRemove2Start = 'function triggerUpload() {';
const funcToRemove2End = "router.push('/login')\n}";
const funcToRemove2Regex = new RegExp(funcToRemove2Start.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + funcToRemove2End.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\n');
content = content.replace(funcToRemove2Regex, '');

// onBeforeUnmount
const onBeforeUnmountBlock = `onBeforeUnmount(() => {
  if (drawRafId != null) {
    window.cancelAnimationFrame(drawRafId)
    drawRafId = null
  }
  if (passwordCodeTimer != null) {
    window.clearInterval(passwordCodeTimer)
    passwordCodeTimer = null
  }
})`;
content = content.replace(onBeforeUnmountBlock, '');

// 5. Remove unused styles
const stylesToRemoveRegex = /\\.settings-modal-scroll[\\s\\S]*?\\.search-buttons-group {/g;
// Wait, we can just remove all styles from .settings-modal-scroll to the end of styles except keep the search panel? 
// No, ProfileSearchPanel handles search panel styles? Let's keep search panel styles just in case, or remove settings ones.
// It's safer to just replace .settings-modal-scroll to .crop-action-row.
const styleStart = '.settings-modal-scroll {';
const styleEnd = '.crop-action-row {\n  display: flex;\n  justify-content: flex-end;\n  gap: 8px;\n}';
const styleRegex = new RegExp(styleStart.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' + styleEnd.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
content = content.replace(styleRegex, '');

fs.writeFileSync(path, content, 'utf8');
console.log('Successfully updated UserProfilePage.vue');
