<template>
  <view class="settings-page" :style="globalThemeStyle">
    <GlobalUI />
    <view class="header">
      <text class="page-title">数据合并</text>
      <text class="page-subtitle">把已有账户的数据带回微信小程序</text>
    </view>

    <view v-if="loading" class="loading-state">正在检查账户状态...</view>
    <view v-else class="merge-content">
      <view class="merge-note"><text class="note-mark">M</text><view><text class="note-title">继续使用已有记录</text><text class="note-text">绑定邮箱后，可将该邮箱账户下的日记、记忆与成长数据合并到当前微信账户。</text></view></view>
      <view class="merge-sheet" @click="showEmailModal = true"><view><text class="row-label">合并邮箱</text><text class="row-hint">{{ originalEmail || '尚未绑定，可随时开始' }}</text></view><view class="merge-action"><text>{{ originalEmail ? '更换' : '去绑定' }}</text><text class="arrow">›</text></view></view>
      <text class="privacy-note">不会公开你的邮箱或日记内容。若该邮箱已有账户，验证完成后将合并为同一个账号。</text>
    </view>

    <view v-if="!loading && personaReady" class="persona-section">
      <view class="section-heading">
        <text class="section-title">AI 个性</text>
        <text class="section-hint">自定义 MoodCopilot 的互动方式</text>
      </view>
      <view class="persona-sheet">
        <text class="field-label">互动身份</text>
        <picker :range="personaRoleLabels" :value="personaRoleIndex" @change="onPersonaRoleChange">
          <view class="picker-field"><text>{{ personaRoleLabels[personaRoleIndex] }}</text><text class="picker-arrow">›</text></view>
        </picker>
        <text class="field-label">语气</text>
        <checkbox-group class="choice-grid" @change="onPersonaToneChange">
          <label v-for="option in personaToneOptions" :key="option.value" class="choice-item">
            <checkbox :value="option.value" :checked="persona.tone.includes(option.value)" :color="currentTheme.primary" />
            <text>{{ option.label }}</text>
          </label>
        </checkbox-group>
        <text class="field-label">自定义语气</text>
        <input v-model="persona.customTone" class="persona-input" maxlength="160" placeholder="例如：冷静务实，像可靠的前辈" />
        <text class="persona-field-help">用一句话补充预设之外的表达感觉，只影响语气。</text>
        <text class="field-label">回答方式</text>
        <checkbox-group class="choice-grid" @change="onPersonaBehaviorChange">
          <label v-for="option in personaBehaviorOptions" :key="option.value" class="choice-item">
            <checkbox :value="option.value" :checked="persona.behaviorFlags.includes(option.value)" :color="currentTheme.primary" />
            <text>{{ option.label }}</text>
          </label>
        </checkbox-group>
        <text class="field-label">自定义回答方式</text>
        <textarea v-model="persona.customResponseStyle" class="persona-textarea" maxlength="800" placeholder="例如：按“事实、判断、建议”分开说明，并明确标注不确定信息" />
        <text class="persona-field-help">只影响回答组织方式。</text>
        <view class="persona-actions">
          <button class="persona-save" :disabled="savingPersona" @click="savePersona">{{ savingPersona ? '保存中...' : '保存 AI 个性' }}</button>
          <button class="persona-reset" :disabled="savingPersona" @click="restoreDefaultPersona">恢复默认</button>
        </view>
        <text v-if="personaMessage" class="persona-message">{{ personaMessage }}</text>
        <text class="persona-note">设置只影响表达方式，不会改变数据权限或模型选择。</text>

        <view class="persona-preview">
          <view class="persona-preview-heading">
            <text class="field-label persona-preview-label">试试看</text>
            <picker :range="personaPreviewModeLabels" :value="personaPreviewModeIndex" @change="onPersonaPreviewModeChange">
              <view class="persona-preview-mode"><text>{{ personaPreviewModeLabels[personaPreviewModeIndex] }}</text><text class="picker-arrow">›</text></view>
            </picker>
          </view>
          <textarea v-model="personaPreviewMessage" class="persona-textarea persona-preview-input" maxlength="1000" placeholder="输入一句示例问题，例如：帮我审查这段代码" />
          <button class="persona-preview-button" :disabled="previewingPersona || !personaPreviewMessage.trim()" @click="previewPersona">{{ previewingPersona ? '生成中...' : '预览回答方式' }}</button>
          <text v-if="personaPreviewResult" class="persona-preview-result">{{ personaPreviewResult }}</text>
          <text v-if="personaPreviewError" class="persona-preview-error">{{ personaPreviewError }}</text>
          <text class="persona-preview-note">预览不会读取或写入日记、记忆、事件和聊天记录。</text>
        </view>
      </view>
    </view>

    <!-- Email Modal -->
    <view class="modal-overlay" v-if="showEmailModal" @click="showEmailModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">{{ originalEmail ? '更换合并邮箱' : '验证并合并账户' }}</text>
        <view class="email-form">
          <input class="modal-input" v-model="bindForm.email" placeholder="输入已有账户的邮箱" />
          <view class="code-row">
            <input class="modal-input code-input" v-model="bindForm.code" placeholder="验证码" />
            <button class="code-btn" :disabled="countdown > 0" @click="sendBindCode">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</button>
          </view>
        </view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="showEmailModal = false">取消</button>
          <button class="confirm-btn" @click="submitBindEmail">确认合并</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">

import { ref, onMounted, onUnmounted } from 'vue';
import { get, post, put } from '@/utils/request';
import GlobalUI from '@/components/GlobalUI.vue';
import { currentTheme } from '@/stores/theme';

const loading = ref(true);

const originalEmail = ref('');
const countdown = ref(0);
const showEmailModal = ref(false);

const bindForm = ref({
  email: '',
  code: ''
});

const personaRoleOptions = [
  { value: 'personal_assistant', label: '通用个人助手' },
  { value: 'study_partner', label: '学习伙伴' },
  { value: 'coding_partner', label: '编程协作伙伴' },
  { value: 'writing_partner', label: '写作伙伴' },
  { value: 'life_companion', label: '生活陪伴者' },
];
const personaRoleLabels = personaRoleOptions.map(item => item.label);
const personaToneOptions = [
  { value: 'natural', label: '自然' }, { value: 'warm', label: '温和' },
  { value: 'direct', label: '直接' }, { value: 'clear', label: '清晰' },
  { value: 'concise', label: '简洁' }, { value: 'precise', label: '严谨' },
  { value: 'formal', label: '正式' }, { value: 'playful', label: '轻松' },
  { value: 'empathetic', label: '共情' }, { value: 'calm', label: '沉静' },
  { value: 'analytical', label: '分析型' }, { value: 'encouraging', label: '鼓励' },
  { value: 'humorous', label: '幽默' }, { value: 'critical', label: '批判思考' },
];
const personaBehaviorOptions = [
  { value: 'CONCLUSION_FIRST', label: '先说结论' }, { value: 'ASK_WHEN_AMBIGUOUS', label: '不明确时先追问' },
  { value: 'CODE_FIRST', label: '代码优先' }, { value: 'LESS_REASSURANCE', label: '少一些安慰' },
  { value: 'DIRECT_FEEDBACK', label: '直接反馈' }, { value: 'STEP_BY_STEP', label: '分步骤说明' },
  { value: 'CONCISE', label: '控制篇幅' },
];
const persona = ref({ role: 'personal_assistant', tone: ['natural', 'clear'], behaviorFlags: ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'], disabledBehaviorFlags: [] as string[], customTone: '', customResponseStyle: '' });
const personaRoleIndex = ref(0);
const personaReady = ref(false);
const savingPersona = ref(false);
const personaMessage = ref('');
const personaPreviewMessage = ref('帮我审查这段代码');
const personaPreviewModeLabels = ['快速预览', '深度预览'];
const personaPreviewModeIndex = ref(0);
const previewingPersona = ref(false);
const personaPreviewResult = ref('');
const personaPreviewError = ref('');
const defaultPersona = () => ({
  role: 'personal_assistant',
  tone: ['natural', 'clear'],
  behaviorFlags: ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'],
  disabledBehaviorFlags: [] as string[],
  customTone: '',
  customResponseStyle: '',
});

let countdownTimer: ReturnType<typeof setInterval> | null = null;

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
});



onMounted(() => {
  fetchProfile();
});

const fetchProfile = async () => {
  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data) {
      const u = res.data.user || res.data;
      let email = u.email || '';
      if (email.includes('@wx.com')) {
        email = '';
      }
      originalEmail.value = email;
      bindForm.value.email = ''; // clear for new binding
    }
    const personaRes = await get('/api/auth/ai-persona');
    if (personaRes.code === 200 && personaRes.data) {
      const data = personaRes.data as any;
      persona.value = {
        role: data.role || 'personal_assistant',
        tone: Array.isArray(data.tone) && data.tone.length ? data.tone : ['natural', 'clear'],
        behaviorFlags: Array.isArray(data.behaviorFlags) ? data.behaviorFlags : ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'],
        disabledBehaviorFlags: Array.isArray(data.disabledBehaviorFlags) ? data.disabledBehaviorFlags : [],
        customTone: data.customTone || '',
        customResponseStyle: data.customResponseStyle || '',
      };
      const roleIndex = personaRoleOptions.findIndex(item => item.value === persona.value.role);
      personaRoleIndex.value = roleIndex < 0 ? 0 : roleIndex;
    }
    personaReady.value = true;
  } catch (e) {
    console.error('Failed to fetch profile', e);
    personaReady.value = true;
  } finally {
    loading.value = false;
  }
};

const onPersonaRoleChange = (event: any) => {
  personaRoleIndex.value = Number(event.detail.value);
  persona.value.role = personaRoleOptions[personaRoleIndex.value].value;
};

const onPersonaToneChange = (event: any) => {
  persona.value.tone = event.detail.value || [];
};

const onPersonaBehaviorChange = (event: any) => {
  persona.value.behaviorFlags = event.detail.value || [];
};

const savePersona = async () => {
  if (savingPersona.value) return;
  savingPersona.value = true;
  personaMessage.value = '';
  try {
    const res = await put('/api/auth/ai-persona', { ...persona.value, customTone: persona.value.customTone.trim(), customResponseStyle: persona.value.customResponseStyle.trim() });
    if (res.code === 200) {
      personaMessage.value = 'AI 个性已更新';
      uni.showToast({ title: '保存成功', icon: 'success' });
    } else {
      personaMessage.value = res.message || '保存失败';
    }
  } catch (e: any) {
    personaMessage.value = e.message || '保存失败';
  } finally {
    savingPersona.value = false;
  }
};

const restoreDefaultPersona = () => {
  if (savingPersona.value) return;
  uni.showModal({
    title: '恢复默认 AI 个性',
    content: '这会保存一份新的默认设置，之前的设置仍会保留在版本记录中。',
    confirmText: '恢复默认',
    success: result => {
      if (!result.confirm) return;
      persona.value = defaultPersona();
      personaRoleIndex.value = 0;
      savePersona();
    },
  });
};

const onPersonaPreviewModeChange = (event: any) => {
  personaPreviewModeIndex.value = Number(event.detail.value);
};

const previewPersona = async () => {
  if (previewingPersona.value || !personaPreviewMessage.value.trim()) return;
  previewingPersona.value = true;
  personaPreviewResult.value = '';
  personaPreviewError.value = '';
  try {
    const res = await post('/api/auth/ai-persona/preview', {
      persona: { ...persona.value, customTone: persona.value.customTone.trim() },
      sampleMessage: personaPreviewMessage.value.trim(),
      useReasoning: personaPreviewModeIndex.value === 1,
    });
    if (res.code === 200 && typeof res.data === 'string') {
      personaPreviewResult.value = res.data;
    } else {
      personaPreviewError.value = res.message || '暂时无法生成预览';
    }
  } catch (e: any) {
    personaPreviewError.value = e?.message || '暂时无法生成预览';
  } finally {
    previewingPersona.value = false;
  }
};

const sendBindCode = async () => {
  if (!bindForm.value.email.trim() || countdown.value > 0) return;
  uni.showLoading({ title: '发送中' });
  try {
    const res = await post('/api/auth/bind-email/send-code', { email: bindForm.value.email.trim() });
    if (res.code === 200) {
      uni.showToast({ title: '已发送', icon: 'success' });
      countdown.value = 60;
      countdownTimer = setInterval(() => {
        countdown.value--;
        if (countdown.value <= 0) {
          if (countdownTimer) clearInterval(countdownTimer);
          countdownTimer = null;
        }
      }, 1000);
    } else {
      uni.showToast({ title: res.message || '发送失败', icon: 'none' });
    }
  } catch(e) {
    uni.showToast({ title: '发送失败', icon: 'none' });
  } finally {
    uni.hideLoading();
  }
};

const submitBindEmail = async () => {
  if (!bindForm.value.email.trim() || !bindForm.value.code.trim()) {
    uni.showToast({ title: '请填写完整', icon: 'none' });
    return;
  }
  uni.showLoading({ title: '绑定中...' });
  try {
    const bindRes = await post('/api/auth/bind-email', { email: bindForm.value.email.trim(), code: bindForm.value.code.trim() });
    if (bindRes.code === 200) {
      if (bindRes.data) {
        uni.setStorageSync('token', bindRes.data);
        uni.showToast({ title: '账号合并成功', icon: 'success' });
        // Emit event to refresh feed/profile
        uni.$emit('profileUpdated');
        uni.$emit('refreshFeed');
      } else {
        uni.showToast({ title: '绑定成功', icon: 'success' });
      }
      originalEmail.value = bindForm.value.email.trim();
      showEmailModal.value = false;
      bindForm.value.code = '';
    } else {
      uni.showToast({ title: bindRes.message || '绑定失败', icon: 'none' });
    }
  } catch (e) {
    uni.showToast({ title: '绑定失败', icon: 'none' });
  } finally {
    uni.hideLoading();
  }
};

</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 40rpx;
  box-sizing: border-box;
}

.loading-state { padding: 160rpx 0; text-align: center; color: var(--theme-text-secondary); font-size: 28rpx; }
.merge-content { padding-top: 8rpx; }
.merge-note { display: flex; gap: 20rpx; padding: 30rpx; background: color-mix(in oklab, var(--theme-primary) 7%, var(--theme-surface)); border-left: 4rpx solid var(--theme-primary); }
.note-mark { display: flex; width: 56rpx; height: 56rpx; align-items: center; justify-content: center; background: var(--theme-primary); color: var(--theme-text-on-primary); font-family: "Noto Serif SC", serif; font-size: 30rpx; flex-shrink: 0; }
.note-title { display: block; color: var(--theme-text-primary); font-family: "Noto Serif SC", serif; font-size: 32rpx; }
.note-text { display: block; margin-top: 8rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.65; }
.merge-sheet { display: flex; align-items: center; justify-content: space-between; margin-top: 36rpx; padding: 30rpx; background: var(--theme-surface); border: 1px solid var(--theme-border); border-radius: 8rpx; box-shadow: var(--theme-shadow-panel); }
.row-label { display: block; color: var(--theme-text-primary); font-size: 30rpx; font-weight: 600; }
.row-hint { display: block; margin-top: 6rpx; color: var(--theme-text-placeholder); font-size: 24rpx; }
.merge-action { display: flex; align-items: center; gap: 8rpx; color: var(--theme-primary); font-size: 26rpx; }
.privacy-note { display: block; margin: 26rpx 8rpx; color: var(--theme-text-placeholder); font-size: 23rpx; line-height: 1.65; }

.persona-section { margin-top: 56rpx; }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 16rpx; margin-bottom: 18rpx; }
.section-title { color: var(--theme-text-primary); font-size: 34rpx; font-weight: 600; }
.section-hint { color: var(--theme-text-secondary); font-size: 23rpx; }
.persona-sheet { padding: 30rpx; background: var(--theme-surface); border: 1px solid var(--theme-border); border-radius: 8rpx; }
.field-label { display: block; margin: 0 0 12rpx; color: var(--theme-text-secondary); font-size: 25rpx; font-weight: 600; }
.field-label:not(:first-child) { margin-top: 28rpx; }
.picker-field { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 24rpx; border: 1px solid var(--theme-border); border-radius: 8rpx; color: var(--theme-text-primary); font-size: 28rpx; }
.picker-arrow { color: var(--theme-text-secondary); font-size: 38rpx; line-height: 1; }
.choice-grid { display: flex; flex-wrap: wrap; gap: 14rpx; }
.choice-item { display: inline-flex; align-items: center; gap: 6rpx; padding: 12rpx 14rpx; border: 1px solid var(--theme-border); border-radius: 8rpx; color: var(--theme-text-secondary); font-size: 24rpx; }
.persona-textarea { display: block; width: 100%; min-height: 150rpx; padding: 20rpx; box-sizing: border-box; border: 1px solid var(--theme-border); border-radius: 8rpx; background: var(--theme-bg); color: var(--theme-text-primary); font-size: 27rpx; line-height: 1.55; }
.persona-input { display: block; width: 100%; min-height: 76rpx; padding: 0 20rpx; box-sizing: border-box; border: 1px solid var(--theme-border); border-radius: 8rpx; background: var(--theme-bg); color: var(--theme-text-primary); font-size: 27rpx; }
.persona-field-help { display: block; margin-top: 10rpx; color: var(--theme-text-secondary); font-size: 23rpx; line-height: 1.5; }
.persona-actions { display: flex; gap: 16rpx; margin-top: 28rpx; }
.persona-save, .persona-reset { flex: 1; margin: 0; border-radius: 8rpx; font-size: 28rpx; }
.persona-save { background: var(--theme-primary); color: var(--theme-text-on-primary); }
.persona-reset { background: var(--theme-surface-hover); color: var(--theme-text-primary); }
.persona-save[disabled], .persona-reset[disabled] { opacity: .55; }
.persona-save::after, .persona-reset::after, .persona-preview-button::after { border: none; }
.persona-message { display: block; margin-top: 14rpx; color: var(--theme-primary); font-size: 24rpx; }
.persona-note { display: block; margin-top: 12rpx; color: var(--theme-text-placeholder); font-size: 22rpx; line-height: 1.55; }
.persona-preview { margin-top: 32rpx; padding-top: 28rpx; border-top: 1rpx solid var(--theme-border); }
.persona-preview-heading { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.persona-preview-label { margin: 0; }
.persona-preview-mode { display: flex; align-items: center; gap: 8rpx; color: var(--theme-text-secondary); font-size: 24rpx; }
.persona-preview-input { min-height: 120rpx; margin-top: 14rpx; }
.persona-preview-button { margin: 18rpx 0 0; background: var(--theme-surface-hover); color: var(--theme-text-primary); border: 1rpx solid var(--theme-border); border-radius: 8rpx; font-size: 26rpx; }
.persona-preview-button[disabled] { opacity: .55; }
.persona-preview-result, .persona-preview-error { display: block; margin-top: 16rpx; padding: 20rpx; border: 1rpx solid var(--theme-border); border-radius: 8rpx; background: var(--theme-bg); font-size: 25rpx; line-height: 1.65; white-space: pre-wrap; }
.persona-preview-result { color: var(--theme-text-primary); }
.persona-preview-error { color: var(--theme-text-secondary); }
.persona-preview-note { display: block; margin-top: 14rpx; color: var(--theme-text-placeholder); font-size: 22rpx; line-height: 1.55; }

.header {
  margin-top: 20rpx;
  margin-bottom: 60rpx;
}

.page-title {
  font-size: 48rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
}

.email-right {
  display: flex;
  align-items: center;
}

.email-text {
  font-size: 28rpx;
  color: var(--theme-text-placeholder);
  margin-right: 16rpx;
}

.email-text.is-bound {
  color: var(--theme-primary);
}


.code-btn {
  font-size: 26rpx;
  color: var(--theme-text-on-primary);
  background-color: var(--theme-primary);
  padding: 0 32rpx;
  border-radius: 12rpx;
  height: 90rpx;
  line-height: 90rpx;
  margin: 0 0 0 16rpx;
  white-space: nowrap;
}

.code-btn::after {
  display: none;
}

.code-btn[disabled] {
  color: var(--theme-text-placeholder);
  background-color: var(--theme-surface-hover);
}

.avatar-right {
  display: flex;
  align-items: center;
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  margin-right: 16rpx;
}
.page-subtitle { display: block; margin-top: 8rpx; color: var(--theme-text-secondary); font-size: 27rpx; }
.avatar-fallback { display: flex; align-items: center; justify-content: center; background: var(--theme-primary); color: var(--theme-text-on-primary); font-size: 32rpx; font-weight: 600; }

.arrow {
  font-size: 40rpx;
  color: var(--theme-text-placeholder);
}

.submit-btn {
  margin-top: 48rpx;
  background-color: var(--theme-primary);
  color: var(--theme-surface);
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: bold;
  padding: 24rpx 0;
  line-height: 1.5;
  transition: opacity 0.2s;
}

.submit-btn.disabled {
  opacity: 0.5;
}

/* Modal styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: var(--theme-overlay);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  width: 85%;
  background-color: var(--theme-surface);
  border-radius: var(--theme-radius-lg);
  padding: 40rpx;
  box-shadow: var(--theme-shadow-dialog);
  box-sizing: border-box;
}

.modal-title {
  font-size: 36rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
  margin-bottom: 32rpx;
  display: block;
  text-align: center;
}

.email-form {
  margin-bottom: 32rpx;
}

.modal-input {
  width: 100%;
  background: var(--theme-bg);
  border-radius: 12rpx;
  padding: 24rpx;
  font-size: 30rpx;
  border: 1rpx solid var(--theme-border);
  box-sizing: border-box;
  margin-bottom: 24rpx;
}

.code-row {
  display: flex;
  align-items: center;
}

.code-input {
  flex: 1;
  margin-bottom: 0;
}

.modal-actions {
  display: flex;
  justify-content: space-between;
  gap: 24rpx;
  margin-top: 16rpx;
}

.cancel-btn, .confirm-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 40rpx;
  font-size: 30rpx;
  margin: 0;
}

.cancel-btn::after, .confirm-btn::after {
  border: none;
}

.cancel-btn {
  background-color: var(--theme-surface-hover);
  color: var(--theme-text-secondary);
}

.confirm-btn {
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
}
</style>

<style scoped>
.settings-page { padding: 28rpx var(--theme-page-padding) calc(112rpx + env(safe-area-inset-bottom)); }
.header { margin: 20rpx 0 34rpx; padding-bottom: 14rpx; border-bottom: 1rpx solid var(--theme-border); }
.page-title { font-family: "Noto Serif SC", "Songti SC", "STSong", serif; font-size: 44rpx; }
.merge-note, .merge-sheet { border-radius: var(--theme-radius-md); }
.merge-sheet { box-shadow: none; }
.modal-content { border-radius: var(--theme-radius-lg); box-shadow: var(--theme-shadow-dialog); }
.modal-input { border-color: var(--theme-border); border-radius: var(--theme-radius-sm); background: var(--theme-bg); }
.code-btn, .submit-btn, .confirm-btn { border-radius: var(--theme-radius-sm); background: var(--theme-primary); color: var(--theme-text-on-primary); }
.cancel-btn { border-radius: var(--theme-radius-sm); background: var(--theme-surface-hover); }
</style>
