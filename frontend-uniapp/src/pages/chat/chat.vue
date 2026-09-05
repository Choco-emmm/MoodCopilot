<template>
  <view class="chat-page" :style="globalThemeStyle">
    <GlobalUI :tabIndex="2" />
    <scroll-view 
      class="chat-scroll" 
      scroll-y 
      :scroll-into-view="scrollToMessage"
      :scroll-with-animation="true"
    >
      <view class="sub-header" v-if="conversationId">
        <view class="session-context">
          <text class="session-label">本次对话</text>
          <text class="current-session-title">{{ currentConversationTitle }}</text>
        </view>
        <view class="header-actions">
          <view class="history-btn persona-action" @click="openPersonaPanel">
            <text class="persona-action-label">风格</text>
          </view>
          <view class="history-btn new-chat-action" @click="createNewChat">
            <text class="tool-symbol">+</text>
          </view>
          <view class="history-btn" @click="showDrawer = true">
            <text class="tool-symbol">•••</text>
          </view>
        </view>
      </view>

      <view class="chat-container">
        <!-- 侧边栏抽屉：历史对话 -->
        <view class="drawer-overlay" v-if="showDrawer" @click="showDrawer = false">
          <view class="drawer-content" @click.stop>
            <view class="drawer-header">
              <text class="drawer-title">所有对话</text>
              <button class="new-chat-btn" @click="createNewChat">＋ 新聊天</button>
            </view>
            <scroll-view scroll-y class="drawer-list">
              <view 
                v-for="conv in conversations" 
                :key="conv.id" 
                class="conv-item"
                :class="{ active: conv.id === conversationId }"
                @click="switchConversation(conv.id)"
              >
                <text class="conv-title">{{ displayConversationTitle(conv.title) }}</text>
                <text class="conv-date">{{ formatDate(conv.updatedAt || conv.createdAt) }}</text>
              </view>
            </scroll-view>
          </view>
        </view>

        <!-- 欢迎语或加载 -->
        <view v-if="loadingInit" class="system-message">
          <text>MoodCopilot 正在连接...</text>
        </view>
        <view v-else-if="messages.length === 0" class="welcome-section fade-in">
          <view class="welcome-mark">
            <image src="/static/ai_avatar.png" class="welcome-avatar" mode="aspectFill" />
          </view>
          <text class="welcome-brand">MoodCopilot</text>
          <text class="welcome-copy">可以聊聊最近的心情，分享你的故事和想法</text>
          <view v-if="welcomeTopics.length > 0" class="welcome-topics">
            <text class="topics-title">从一句话开始</text>
            <view class="topics-list">
              <view 
                v-for="(topic, idx) in welcomeTopics" 
                :key="idx" 
                class="topic-btn hover-scale"
                @click="sendTopic(topic)"
              >
                <text class="topic-icon">{{ parseTopic(topic).icon || '✨' }}</text>
                <text class="topic-text">{{ parseTopic(topic).text || topic }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 消息列表 -->
        <view 
          v-for="(msg, index) in messages" 
          :key="index"
          :id="'msg-' + index"
          class="message-row"
          :class="msg.role === 'user' ? 'message-right' : 'message-left'"
        >
          <view v-if="msg.role === 'assistant'" class="avatar ai-avatar">
            <image src="/static/ai_avatar.png" class="avatar-img" mode="aspectFill" />
          </view>
          <view class="bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'" @longpress="handleLongPress(msg)">
            <rich-text class="message-text" :nodes="parseMarkdown(formatMessage(msg.content))"></rich-text>
          </view>
          <view v-if="msg.role === 'user'" class="avatar user-avatar">
            <image v-if="userInfo?.avatar" :src="getFullUrl(userInfo.avatar)" class="avatar-img" mode="aspectFill" />
            <text v-else style="font-size: 28rpx;">我</text>
          </view>
        </view>

        <!-- 等待回复指示?-->
        <view v-if="isWaiting" id="msg-waiting" class="message-row message-left">
          <view class="avatar ai-avatar">
            <image src="/static/ai_avatar.png" class="avatar-img" mode="aspectFill" />
          </view>
          <view class="bubble ai-bubble typing-indicator">
            <text class="dot">.</text><text class="dot">.</text><text class="dot">.</text>
          </view>
        </view>
        
        <!-- 底部占位符，确保滚动到底部不被输入框挡住 -->
        <view class="scroll-bottom-pad"></view>
      </view>
    </scroll-view>

    <!-- 底部输入框区域 -->
    <view class="chat-bottom-wrapper">
      <view class="chat-model-row">
        <text class="chat-model-label">回复模式</text>
        <picker :range="chatModelOptions" :value="useReasoning ? 1 : 0" @change="onChatModelChange">
          <view class="chat-model-picker">{{ useReasoning ? '深度思考' : '普通对话' }} <text class="chat-model-arrow">⌄</text></view>
        </picker>
      </view>
      <!-- 引用预览 -->
      <view v-if="activeQuote" class="quote-preview-bar fade-in">
        <text class="quote-icon">❝</text>
        <text class="quote-text">{{ activeQuote }}</text>
        <view class="quote-close" @click="clearQuote">×</view>
      </view>
      <view v-if="eventReference" class="quote-preview-bar event-reference-preview fade-in">
        <text class="quote-icon">⌁</text>
        <text class="quote-text">事件：{{ eventReference.title }}</text>
        <view class="quote-close" @click="clearEventReference">×</view>
      </view>
      
      <view class="chat-input-bar">
        <view class="reference-actions">
          <view class="quote-action-btn hover-scale" @click="openDiarySelector">
            <text class="quote-action-icon">+</text>
          </view>
          <view class="event-quote-action-btn hover-scale" @click="openEventSelector">
            <text class="quote-action-icon">⌁</text>
          </view>
        </view>
        <input 
          class="chat-input" 
          v-model="inputContent" 
          placeholder="聊聊你今天的心情..."
          :adjust-position="true"
          :cursor-spacing="20"
          @confirm="sendMessage"
        />
        <button 
          class="send-btn" 
          :class="{ disabled: !inputContent.trim() || isWaiting }"
          @click="sendMessage"
        >
          发送
        </button>
      </view>
    </view>

    <!-- 当前会话 Persona 覆盖 -->
    <view v-if="showPersonaPanel" class="persona-mask" @click="showPersonaPanel = false">
      <view class="persona-sheet" @click.stop>
        <view class="persona-sheet-header">
          <view>
            <text class="persona-sheet-title">本次对话风格</text>
            <text class="persona-sheet-hint">{{ conversationPersonaUsesGlobal ? '当前正在使用全局设置' : '当前会话正在使用独立设置' }}</text>
          </view>
          <text class="sheet-close" @click="showPersonaPanel = false">×</text>
        </view>

        <scroll-view scroll-y class="persona-sheet-content">
          <text class="persona-field-label">互动身份</text>
          <picker :range="personaRoleLabels" :value="personaRoleIndex" @change="onPersonaRoleChange">
            <view class="persona-picker-field">
              <text>{{ personaRoleLabels[personaRoleIndex] }}</text>
              <text class="persona-picker-arrow">›</text>
            </view>
          </picker>

          <text class="persona-field-label">语气</text>
          <checkbox-group class="persona-choice-grid" @change="onPersonaToneChange">
            <label v-for="option in personaToneOptions" :key="option.value" class="persona-choice-item">
              <checkbox :value="option.value" :checked="conversationPersona.tone.includes(option.value)" :color="currentTheme.primary" />
              <text>{{ option.label }}</text>
            </label>
          </checkbox-group>

          <text class="persona-field-label">自定义语气</text>
          <input v-model="conversationPersona.customTone" class="persona-input" maxlength="160" placeholder="例如：冷静务实，像可靠的前辈" />
          <text class="persona-field-help">用一句话描述希望听起来怎样，只影响表达风格。</text>

          <text class="persona-field-label">回答方式</text>
          <checkbox-group class="persona-choice-grid" @change="onPersonaBehaviorChange">
            <label v-for="option in personaBehaviorOptions" :key="option.value" class="persona-choice-item">
              <checkbox :value="option.value" :checked="conversationPersona.behaviorFlags.includes(option.value)" :color="currentTheme.primary" />
              <text>{{ option.label }}</text>
            </label>
          </checkbox-group>
          <text class="persona-field-label">自定义回答方式</text>
          <textarea v-model="conversationPersona.customResponseStyle" class="persona-textarea" maxlength="800" placeholder="例如：按“事实、判断、建议”分开说明，并明确标注不确定信息" />
          <text class="persona-field-help">只影响回答组织方式。</text>

          <view class="persona-actions">
            <button class="persona-save" :disabled="personaSaving" @click="saveConversationPersona">
              {{ personaSaving ? '保存中...' : '应用到本次对话' }}
            </button>
            <button class="persona-reset" :disabled="personaSaving" @click="resetConversationPersona">恢复全局设置</button>
          </view>
          <text v-if="personaMessage" class="persona-message">{{ personaMessage }}</text>
        </scroll-view>
      </view>
    </view>

    <!-- 引用事件选择面板 -->
    <view class="diary-selector-mask fade-in" v-if="showEventSelector" @click="showEventSelector = false">
      <view class="diary-selector-sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">选择事件</text>
          <text class="sheet-close" @click="showEventSelector = false">×</text>
        </view>
        <scroll-view scroll-y class="sheet-content">
          <view v-if="loadingEvents" class="sheet-loading">加载中...</view>
          <view v-else-if="recentEvents.length === 0" class="sheet-empty">暂无重要事件</view>
          <view
            v-else
            v-for="event in recentEvents"
            :key="event.id"
            class="sheet-diary-item hover-scale"
            @click="selectEventForQuote(event)"
          >
            <text class="sheet-diary-date">{{ event.targetDate || '时间未填写' }}</text>
            <text class="sheet-diary-content">{{ event.title }}</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 引用日记选择面板 -->
    <view class="diary-selector-mask fade-in" v-if="showDiarySelector" @click="showDiarySelector = false">
      <view class="diary-selector-sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">选择日记</text>
          <text class="sheet-close" @click="showDiarySelector = false">×</text>
        </view>
        <scroll-view scroll-y class="sheet-content">
          <view v-if="loadingDiaries" class="sheet-loading">加载中...</view>
          <view v-else-if="recentDiaries.length === 0" class="sheet-empty">最近没有写日记哦</view>
          <view 
            v-else 
            v-for="diary in recentDiaries" 
            :key="diary.id" 
            class="sheet-diary-item hover-scale"
            @click="selectDiaryForQuote(diary)"
          >
            <text class="sheet-diary-date">{{ formatDate(diary.createdAt) }}</text>
            <text class="sheet-diary-content">{{ extractPlainText(diary.content) }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue';
import { get, post, put, del, getFullUrl } from '@/utils/request';
import { parseMarkdown, extractPlainText } from '@/utils/markdown';
import GlobalUI from '@/components/GlobalUI.vue';
import { hasLoginToken, requireLogin } from '@/stores/login';
import { activeQuote, setQuote, clearQuote } from '@/stores/quote';
import { currentTheme } from '@/stores/theme';
import { displayConversationTitle, isPlaceholderConversationTitle } from '@/utils/chatTitle';

import { onShow } from '@dcloudio/uni-app';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  createdAt?: string;
}

const loadingInit = ref(true);
const userInfo = ref<any>(uni.getStorageSync('userInfo') || null);
const messages = ref<Message[]>([]);
const inputContent = ref('');
const isWaiting = ref(false);
const conversationId = ref<number | null>(null);
const scrollToMessage = ref('');

const showDrawer = ref(false);
const conversations = ref<any[]>([]);
const welcomeTopics = ref<string[]>([]);
const isCreatingConversation = ref(false);
const currentConversationTitle = computed(() => {
  return displayConversationTitle(conversations.value.find(conversation => conversation.id === conversationId.value)?.title);
});

const showDiarySelector = ref(false);
const showEventSelector = ref(false);
const recentDiaries = ref<any[]>([]);
const recentEvents = ref<any[]>([]);
const loadingDiaries = ref(false);
const loadingEvents = ref(false);
const isLoggedIn = ref(hasLoginToken());
const activeEventId = ref<number | null>(null);
const activeDiaryReferenceId = ref<number | null>(null);
const eventReference = ref<{ id: number; title: string } | null>(null);
const useReasoning = ref(false);
const chatModelOptions = ['普通对话', '深度思考'];
const showPersonaPanel = ref(false);
const personaSaving = ref(false);
const personaMessage = ref('');
const personaLoadSequence = ref(0);
const personaRoleOptions = [
  { value: 'personal_assistant', label: '通用个人助手' },
  { value: 'study_partner', label: '学习伙伴' },
  { value: 'coding_partner', label: '编程协作伙伴' },
  { value: 'writing_partner', label: '写作伙伴' },
  { value: 'life_companion', label: '生活陪伴者' },
];
const personaRoleLabels = personaRoleOptions.map(option => option.label);
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
  { value: 'CONCLUSION_FIRST', label: '先说结论' },
  { value: 'ASK_WHEN_AMBIGUOUS', label: '不明确时先追问' },
  { value: 'CODE_FIRST', label: '代码优先' },
  { value: 'LESS_REASSURANCE', label: '少一些安慰' },
  { value: 'DIRECT_FEEDBACK', label: '直接反馈' },
  { value: 'STEP_BY_STEP', label: '分步骤说明' },
  { value: 'CONCISE', label: '控制篇幅' },
];
const conversationPersona = ref({
  role: 'personal_assistant',
  tone: ['natural', 'clear'],
  behaviorFlags: ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'],
  disabledBehaviorFlags: [] as string[],
  customTone: '',
  customResponseStyle: '',
});
const conversationPersonaUsesGlobal = ref(true);
const personaRoleIndex = ref(0);
const globalPersonaSnapshot = ref({ role: 'personal_assistant', tone: ['natural', 'clear'], behaviorFlags: ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'], disabledBehaviorFlags: [] as string[], customTone: '', customResponseStyle: '' });

const onChatModelChange = (event: any) => {
  useReasoning.value = Number(event.detail.value) === 1;
};

const defaultPersona = () => ({
  role: 'personal_assistant',
  tone: ['natural', 'clear'],
  behaviorFlags: ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'],
  disabledBehaviorFlags: [] as string[],
  customTone: '',
  customResponseStyle: '',
});

const applyConversationPersona = (data: any) => {
  const fallback = defaultPersona();
  conversationPersona.value = {
    role: data?.role || fallback.role,
    tone: Array.isArray(data?.tone) && data.tone.length ? data.tone : fallback.tone,
    behaviorFlags: Array.isArray(data?.behaviorFlags) ? data.behaviorFlags : fallback.behaviorFlags,
    disabledBehaviorFlags: Array.isArray(data?.disabledBehaviorFlags) ? data.disabledBehaviorFlags : [],
    customTone: data?.customTone || '',
    customResponseStyle: data?.customResponseStyle || '',
  };
  const index = personaRoleOptions.findIndex(option => option.value === conversationPersona.value.role);
  personaRoleIndex.value = index < 0 ? 0 : index;
};

const loadConversationPersona = async () => {
  const id = conversationId.value;
  if (!id) return;
  const sequence = personaLoadSequence.value + 1;
  personaLoadSequence.value = sequence;
  personaMessage.value = '';
  try {
    const [globalRes, overrideRes] = await Promise.all([
      get<any>('/api/auth/ai-persona'),
      get<any>(`/api/chat/conversations/${id}/persona`),
    ]);
    if (sequence !== personaLoadSequence.value || id !== conversationId.value) return;
    const usesGlobal = !(overrideRes.code === 200 && overrideRes.data);
    conversationPersonaUsesGlobal.value = usesGlobal;
    const global = globalRes.data || {};
    globalPersonaSnapshot.value = {
      role: global.role || 'personal_assistant',
      tone: Array.isArray(global.tone) && global.tone.length ? global.tone : ['natural', 'clear'],
      behaviorFlags: Array.isArray(global.behaviorFlags) ? global.behaviorFlags : ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'],
      disabledBehaviorFlags: Array.isArray(global.disabledBehaviorFlags) ? global.disabledBehaviorFlags : [],
      customTone: global.customTone || '',
      customResponseStyle: global.customResponseStyle || '',
    };
    applyConversationPersona(usesGlobal ? globalRes.data : overrideRes.data);
  } catch (error) {
    if (sequence === personaLoadSequence.value && id === conversationId.value) {
      applyConversationPersona(defaultPersona());
      conversationPersonaUsesGlobal.value = true;
      console.warn('加载本次对话风格失败', error);
    }
  }
};

const openPersonaPanel = () => {
  if (!conversationId.value) return;
  showPersonaPanel.value = true;
  void loadConversationPersona();
};

const onPersonaRoleChange = (event: any) => {
  personaRoleIndex.value = Number(event.detail.value);
  conversationPersona.value.role = personaRoleOptions[personaRoleIndex.value]?.value || 'personal_assistant';
};

const onPersonaToneChange = (event: any) => {
  conversationPersona.value.tone = event.detail.value || [];
};

const onPersonaBehaviorChange = (event: any) => {
  conversationPersona.value.behaviorFlags = event.detail.value || [];
};

const saveConversationPersona = async () => {
  if (!conversationId.value || personaSaving.value) return;
  personaSaving.value = true;
  personaMessage.value = '';
  try {
    const res = await put(`/api/chat/conversations/${conversationId.value}/persona`, {
      ...conversationPersona.value,
      customTone: conversationPersona.value.customTone.trim(),
      customResponseStyle: conversationPersona.value.customResponseStyle.trim(),
      disabledBehaviorFlags: [...new Set([
        ...conversationPersona.value.disabledBehaviorFlags,
        ...globalPersonaSnapshot.value.behaviorFlags.filter(flag => !conversationPersona.value.behaviorFlags.includes(flag)),
      ])],
    });
    if (res.code === 200) {
      conversationPersonaUsesGlobal.value = false;
      personaMessage.value = '本次对话风格已应用';
      uni.showToast({ title: '保存成功', icon: 'success' });
    } else {
      personaMessage.value = res.message || '保存失败';
    }
  } catch (error: any) {
    personaMessage.value = error?.message || '保存失败';
  } finally {
    personaSaving.value = false;
  }
};

const resetConversationPersona = async () => {
  if (!conversationId.value || personaSaving.value) return;
  personaSaving.value = true;
  personaMessage.value = '';
  try {
    const res = await del(`/api/chat/conversations/${conversationId.value}/persona`);
    if (res.code === 200) {
      await loadConversationPersona();
      personaMessage.value = '已恢复全局设置';
    } else {
      personaMessage.value = res.message || '恢复失败';
    }
  } catch (error: any) {
    personaMessage.value = error?.message || '恢复失败';
  } finally {
    personaSaving.value = false;
  }
};

const parseTopic = (topic: string | any) => {
  if (typeof topic === 'string') {
    try {
      const parsed = JSON.parse(topic);
      return typeof parsed === 'object' && parsed !== null ? parsed : { text: topic, icon: '✨' };
    } catch {
      return { text: topic, icon: '✨' };
    }
  }
  return topic || { text: '话题', icon: '✨' };
};

const formatMessage = (content: string) => {
  if (!content) return '';
  return content.replace(/<think>[\s\S]*?<\/think>/gi, '').trim();
};

const formatDate = (isoString: string) => {
  if (!isoString) return '';
  const date = new Date(isoString);
  return `${date.getMonth() + 1}-${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};

onMounted(() => {
  void restorePendingEvent();
  const pendingConversationId = readPendingConversation();
  if (isLoggedIn.value) {
    initConversation(pendingConversationId);
    fetchUserInfo();
  } else {
    loadingInit.value = false;
  }
});

async function restorePendingEvent() {
  const storedEventId = Number(uni.getStorageSync('pendingLifeEventId'));
  if (Number.isFinite(storedEventId) && storedEventId > 0) {
    activeEventId.value = storedEventId;
    uni.removeStorageSync('pendingLifeEventId');
    try {
      const res = await get<any>(`/api/life-events/${storedEventId}`);
      const event = res.code === 200 ? res.data : null;
      eventReference.value = event ? { id: storedEventId, title: event.title } : { id: storedEventId, title: `事件 #${storedEventId}` };
    } catch {
      eventReference.value = { id: storedEventId, title: `事件 #${storedEventId}` };
    }
  }
}

const clearEventReference = () => {
  activeEventId.value = null;
  eventReference.value = null;
};

function readPendingConversation(): number | null {
  const storedId = Number(uni.getStorageSync('pendingChatConversationId'));
  return Number.isFinite(storedId) && storedId > 0 ? storedId : null;
}

onShow(() => {
  void restorePendingEvent();
  const pendingConversationId = readPendingConversation();
  if (pendingConversationId && conversations.value.some(conv => conv.id === pendingConversationId)) {
    uni.removeStorageSync('pendingChatConversationId');
    switchConversation(pendingConversationId);
  }
});

const fetchUserInfo = async () => {
  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data) {
      userInfo.value = res.data.user || res.data;
      uni.setStorageSync('userInfo', userInfo.value);
    }
  } catch (e) {
    console.error('获取用户信息失败', e);
  }
};

const openDiarySelector = async () => {
  if (!isLoggedIn.value) {
    requireLogin();
    return;
  }
  showDiarySelector.value = true;
  loadingDiaries.value = true;
  try {
    const res = await get('/api/diaries/mine?page=1&size=15');
    if (res.code === 200 && res.data) {
      recentDiaries.value = res.data.items || res.data.content || res.data || [];
    }
  } catch (e) {
    console.error('获取日记失败', e);
  } finally {
    loadingDiaries.value = false;
  }
};

const openEventSelector = async () => {
  if (!isLoggedIn.value) {
    requireLogin();
    return;
  }
  showEventSelector.value = true;
  loadingEvents.value = true;
  try {
    const res = await get('/api/life-events');
    if (res.code === 200) recentEvents.value = res.data || [];
  } catch (e) {
    console.error('获取事件失败', e);
    recentEvents.value = [];
  } finally {
    loadingEvents.value = false;
  }
};

const selectDiaryForQuote = (diary: any) => {
  const dateStr = formatDate(diary.createdAt);
  const plain = diary.content ? extractPlainText(diary.content) : '一段没有文字的记录';
  const prefix = `关于我的这篇日记（${dateStr}）：\n\n`;
  setQuote(prefix + plain);
  activeDiaryReferenceId.value = Number(diary.id) || null;
  showDiarySelector.value = false;
};

const selectEventForQuote = (event: any) => {
  activeDiaryReferenceId.value = null;
  activeEventId.value = Number(event.id);
  eventReference.value = { id: Number(event.id), title: event.title || '重要事件' };
  showEventSelector.value = false;
};

const handleLongPress = (msg: Message) => {
  uni.showActionSheet({
    itemList: ['引用', '复制'],
    success: (res) => {
      if (res.tapIndex === 0) {
        activeDiaryReferenceId.value = null;
        setQuote(msg.content);
      } else if (res.tapIndex === 1) {
        uni.setClipboardData({
          data: msg.content,
          success: () => uni.showToast({ title: '已复制', icon: 'none' })
        });
      }
    }
  });
};

const initConversation = async (preferredConversationId: number | null = null) => {
  try {
    const res = await get('/api/chat/conversations');
    if (res.code === 200 && res.data && res.data.length > 0) {
      conversations.value = res.data;
      conversationId.value = res.data.some((conversation: any) => conversation.id === preferredConversationId)
        ? preferredConversationId
        : res.data[0].id;
      loadHistory();
      void loadConversationPersona();
    } else {
      await createNewChat();
    }
  } catch (e) {
    console.error('初始化会话失败', e);
  } finally {
    loadingInit.value = false;
  }
};

const fetchWelcomeTopics = async () => {
  const defaultTopics = [
    JSON.stringify({ icon: '🌟', text: '分析我最近三天的情绪波动' }),
    JSON.stringify({ icon: '💡', text: '帮我回顾我最近开心的事情' }),
    JSON.stringify({ icon: '🌿', text: '推荐一些适合我解压的音乐与方式' }),
    JSON.stringify({ icon: '💬', text: '今天有点累，陪我聊一聊' })
  ];
  
  try {
    const res = await get('/api/chat/welcome-topics');
    if (res.code === 200 && res.data && res.data.length > 0) {
      welcomeTopics.value = res.data;
    } else {
      welcomeTopics.value = defaultTopics;
    }
  } catch (e) {
    console.error('Failed to fetch welcome topics', e);
    welcomeTopics.value = defaultTopics;
  }
};

const createNewChat = async () => {
  if (isCreatingConversation.value) return;
  isCreatingConversation.value = true;
  try {
    const res = await post('/api/chat/conversations', { title: '新聊天' });
    if (res.code === 200) {
      conversationId.value = res.data.id;
      messages.value = [];
      void loadConversationPersona();
      showDrawer.value = false;
      fetchWelcomeTopics();
      // 重新加载列表
      const listRes = await get('/api/chat/conversations');
      if (listRes.code === 200) {
        conversations.value = listRes.data;
      }
    }
  } catch (e) {
    console.error('创建新对话失败', e);
  } finally {
    isCreatingConversation.value = false;
  }
};

const switchConversation = (id: number) => {
  if (conversationId.value === id) {
    showDrawer.value = false;
    return;
  }
  conversationId.value = id;
  messages.value = [];
  showDrawer.value = false;
  loadHistory();
  void loadConversationPersona();
};

const loadHistory = async () => {
  if (!conversationId.value) return;
  try {
    const res = await get(`/api/chat/conversations/${conversationId.value}/history`);
    if (res.code === 200 && res.data) {
      let msgs = [];
      if (Array.isArray(res.data)) {
        msgs = res.data;
      } else if (res.data.messages && Array.isArray(res.data.messages)) {
        msgs = res.data.messages;
      }
      
      messages.value = msgs;
      if (messages.value.length === 0) {
        fetchWelcomeTopics();
      }
      scrollToBottom();
    }
  } catch (e) {
    console.error('加载历史消息失败', e);
  }
};

const sendTopic = (topic: string | any) => {
  const parsed = parseTopic(topic);
  inputContent.value = parsed.text || topic;
  activeEventId.value = parsed.eventId ? Number(parsed.eventId) : null;
  sendMessage();
};

const sendMessage = async () => {
  if (!inputContent.value.trim() || isWaiting.value) return;
  if (!isLoggedIn.value) {
    requireLogin(() => {
      isLoggedIn.value = true;
      void initConversation();
      void fetchUserInfo();
    });
    return;
  }
  if (!conversationId.value) return;

  const content = inputContent.value.trim();
  const isFirstUserMessage = !messages.value.some(message => message.role === 'user');
  const currentQuote = activeQuote.value;
  const eventId = activeEventId.value;
  const referenceItems = [
    activeDiaryReferenceId.value ? { sourceType: 'diary', sourceId: activeDiaryReferenceId.value } : null,
    eventId ? { sourceType: 'event', sourceId: eventId } : null,
  ].filter(Boolean);
  
  messages.value.push({ role: 'user', content, createdAt: new Date().toISOString() });
  inputContent.value = '';
  clearQuote();
  isWaiting.value = true;
  scrollToBottom('waiting');

  try {
    const references = currentQuote ? [currentQuote] : [];
    const res = await post(`/api/chat/conversations/${conversationId.value}/reply`, {
      message: content,
      references: references,
      useReasoning: useReasoning.value,
      ...(referenceItems.length ? { referenceItems } : {}),
      ...(eventId ? { eventId } : {}),
    });
    
    if (res.code === 200) {
      messages.value.push({ role: 'assistant', content: res.data, createdAt: new Date().toISOString() });
    } else {
      messages.value.push({ role: 'assistant', content: '抱歉，我现在有点走神，请稍后再试', createdAt: new Date().toISOString() });
    }
  } catch (e: any) {
    const errorMessage = e?.statusCode === 429 && useReasoning.value
      ? '深度思考额度已用完，请改用普通对话或明日再试。'
      : (e?.message || '网络似乎出了点问题');
    messages.value.push({ role: 'assistant', content: errorMessage, createdAt: new Date().toISOString() });
  } finally {
    isWaiting.value = false;
    activeEventId.value = null;
    activeDiaryReferenceId.value = null;
    eventReference.value = null;
    scrollToBottom();
    if (isFirstUserMessage && conversationId.value) {
      void waitForConversationTitle(conversationId.value);
    }
  }
};

const waitForConversationTitle = async (id: number) => {
  const delays = [700, 1300, 2200, 3500];
  for (const delay of delays) {
    await new Promise(resolve => setTimeout(resolve, delay));
    try {
      const res = await get('/api/chat/conversations');
      if (res.code !== 200 || !Array.isArray(res.data)) continue;
      conversations.value = res.data;
      const conversation = conversations.value.find(item => item.id === id);
      if (conversation && !isPlaceholderConversationTitle(conversation.title)) return;
    } catch (e) {
      console.warn('刷新聊天标题失败', e);
    }
  }
};

const scrollToBottom = (target?: 'waiting') => {
  nextTick(() => {
    if (target === 'waiting') {
      scrollToMessage.value = 'msg-waiting';
    } else {
      scrollToMessage.value = 'msg-' + (messages.value.length - 1);
    }
  });
};
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--theme-bg);
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.chat-scroll {
  flex: 1;
  overflow: hidden;
}

.chat-container {
  padding: 40rpx 32rpx;
}

.system-message {
  text-align: center;
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  margin: 40rpx 0;
}

.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 40rpx;
}

.welcome-topics {
  margin-top: 40rpx;
  width: 100%;
  max-width: 600rpx;
}

.topic-btn {
  background-color: var(--theme-surface);
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: var(--theme-shadow-panel);
  display: flex;
  flex-direction: row;
  align-items: center;
  border: 1rpx solid var(--theme-border);
  transition: color 0.2s ease, background-color 0.2s ease, border-color 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
}

.topic-btn:active {
  transform: scale(0.98);
}

.topic-icon {
  font-size: 36rpx;
  margin-right: 16rpx;
}

.topic-text {
  font-size: 28rpx;
  color: var(--theme-primary);
  flex: 1;
}

.message-row {
  display: flex;
  margin-bottom: 32rpx;
  align-items: flex-start;
}

.message-left {
  justify-content: flex-start;
}
.message-right {
  justify-content: flex-end;
}

.avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: bold;
}
.ai-avatar {
  background-color: transparent;
  color: var(--theme-text-on-primary);
  margin-right: 16rpx;
  overflow: hidden;
}
.user-avatar {
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
  margin-left: 16rpx;
  overflow: hidden;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bubble {
  max-width: 70%;
  padding: 24rpx 32rpx;
  border-radius: 24rpx;
  font-size: 30rpx;
  line-height: 1.5;
  word-break: break-all;
}
.ai-bubble {
  background-color: var(--theme-surface);
  color: var(--theme-text-primary);
  box-shadow: var(--theme-shadow-panel);
  border-top-left-radius: 0;
}
.user-bubble {
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
  border-bottom-right-radius: 0;
}

.message-text {
  font-size: 28rpx;
  line-height: 1.5;
  word-wrap: break-word;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 40rpx;
}
.dot {
  font-size: 36rpx;
  line-height: 1;
  color: var(--theme-text-placeholder);
  animation: typing 1.4s infinite;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  0%, 100% { opacity: 0.2; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-4rpx); }
}

.scroll-bottom-pad {
  height: 120rpx;
}

.chat-bottom-wrapper {
  position: fixed;
  bottom: calc(100rpx + env(safe-area-inset-bottom));
  left: 0;
  width: 100vw;
  box-sizing: border-box;
  background-color: var(--theme-surface);
  border-top: 1rpx solid var(--theme-border);
  z-index: 100;
}

.quote-preview-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 32rpx;
  background-color: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
  border-bottom: 1rpx dashed var(--theme-border);
}

.quote-icon {
  font-size: 32rpx;
  color: var(--theme-primary);
  margin-right: 12rpx;
  opacity: 0.6;
}

.quote-text {
  flex: 1;
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quote-close {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 36rpx;
  color: var(--theme-text-placeholder);
  border-radius: 50%;
  background: var(--theme-surface-hover);
}

.chat-input-bar {
  padding: 24rpx 32rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 24rpx;
}

.chat-input {
  flex: 1;
  height: 80rpx;
  background-color: var(--theme-bg);
  border-radius: 40rpx;
  padding: 0 32rpx;
  font-size: 30rpx;
  color: var(--theme-text-primary);
  border: 1rpx solid var(--theme-border);
}
.send-btn {
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 40rpx;
  padding: 0 40rpx;
  font-size: 28rpx;
  font-weight: 500;
  margin: 0;
}
.send-btn::after {
  display: none;
}
.send-btn.disabled {
  opacity: 0.5;
}

/* 历史记录副导航栏 */
.sub-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 40rpx;
  background-color: var(--theme-bg);
  backdrop-filter: blur(10px);
  border-bottom: 1rpx solid var(--theme-border);
}
.current-session-title {
  font-size: 28rpx;
  color: var(--theme-text-primary);
  font-weight: 500;
}
.history-btn {
  font-size: 26rpx;
  color: var(--theme-primary);
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 16rpx;
  background: color-mix(in oklab, var(--theme-primary) 10%, var(--theme-surface));
  border-radius: 30rpx;
}

/* 抽屉层 */
.drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: var(--theme-overlay);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}
.drawer-content {
  width: 75vw;
  height: 100%;
  background-color: var(--theme-bg);
  box-shadow: var(--theme-shadow-panel);
  display: flex;
  flex-direction: column;
  animation: slideIn 0.3s ease;
}
@keyframes slideIn {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}
.drawer-header {
  padding: 100rpx 32rpx 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1rpx solid var(--theme-border);
}
.drawer-title {
  font-size: 36rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
  font-family: "Noto Serif SC", serif;
}
.new-chat-btn {
  font-size: 24rpx;
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
  border-radius: 30rpx;
  padding: 0 24rpx;
  height: 56rpx;
  line-height: 56rpx;
  margin: 0;
}
.new-chat-btn::after {
  display: none;
}
.drawer-list {
  flex: 1;
  overflow: hidden;
}
.conv-item {
  padding: 32rpx;
  border-bottom: 1rpx solid var(--theme-border);
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.conv-item.active {
  background-color: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
}
.conv-title {
  font-size: 30rpx;
  color: var(--theme-text-primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-date {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
}

/* 日记引用面板 */
.diary-selector-mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: var(--theme-overlay);
  z-index: 99999;
  display: flex;
  justify-content: center;
  align-items: flex-end;
}

.diary-selector-sheet {
  width: 100%;
  height: 60vh;
  background-color: var(--theme-surface);
  border-top-left-radius: var(--theme-radius-lg);
  border-top-right-radius: var(--theme-radius-lg);
  display: flex;
  flex-direction: column;
  box-shadow: var(--theme-shadow-dialog);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.sheet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 40rpx;
  border-bottom: 1rpx solid var(--theme-border);
}

.sheet-title {
  font-size: 32rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
}

.sheet-close {
  font-size: 40rpx;
  color: var(--theme-text-placeholder);
  padding: 10rpx;
}

.sheet-content {
  flex: 1;
  height: 0;
  padding: 32rpx;
  box-sizing: border-box;
}

.sheet-loading, .sheet-empty {
  text-align: center;
  padding: 80rpx 0;
  color: var(--theme-text-placeholder);
  font-size: 28rpx;
}

.sheet-diary-item {
  background-color: var(--theme-surface);
  border-radius: 20rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: none;
  border: 1rpx solid var(--theme-border);
}

.sheet-diary-date {
  font-size: 24rpx;
  color: var(--theme-primary);
  margin-bottom: 16rpx;
  display: block;
}

.sheet-diary-content {
  font-size: 28rpx;
  color: var(--theme-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}

/* Conversation surface: editorial welcome state with compact mobile controls. */
.chat-page { background: var(--theme-bg); }
.sub-header { min-height: 94rpx; padding: 18rpx 32rpx; border-bottom: 1rpx solid var(--theme-border); background: var(--theme-bg); box-sizing: border-box; }
.session-context { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.session-label { color: var(--theme-text-placeholder); font-size: 19rpx; }
.current-session-title { overflow: hidden; margin-top: 4rpx; color: var(--theme-text-primary); font-size: 27rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.header-actions { display: flex; align-items: center; gap: 10rpx; margin-left: 18rpx; }
.history-btn { display: flex; width: 58rpx; height: 58rpx; align-items: center; justify-content: center; padding: 0; border: 1rpx solid var(--theme-border); border-radius: 6rpx; background: var(--theme-surface); color: var(--theme-primary); box-sizing: border-box; }
.tool-symbol { font-size: 31rpx; font-weight: 650; line-height: 1; }
.history-btn:not(.new-chat-action) .tool-symbol { font-size: 24rpx; letter-spacing: 1rpx; }
.persona-action { width: 78rpx; }
.persona-action-label { color: var(--theme-text-secondary); font-size: 22rpx; }
.chat-container { min-height: 100%; padding: 30rpx 32rpx 44rpx; box-sizing: border-box; }
.welcome-section { width: 100%; margin: 32rpx 0 0; padding: 48rpx 28rpx 30rpx; border: 1rpx solid var(--theme-border); border-radius: 10rpx; background: var(--theme-surface); box-sizing: border-box; }
.welcome-mark { display: flex; width: 86rpx; height: 86rpx; align-items: center; justify-content: center; margin: 0 auto 20rpx; border: 1rpx solid var(--theme-border); border-radius: 50%; background: color-mix(in oklab, var(--theme-primary) 6%, var(--theme-surface)); overflow: hidden; }
.welcome-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; }
.welcome-brand { display: block; color: var(--theme-primary); font-family: Georgia, "Times New Roman", serif; font-size: 50rpx; font-weight: 500; text-align: center; }
.welcome-copy { display: block; margin-top: 11rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.65; text-align: center; }
.welcome-section .system-message { display: none; }
.welcome-topics { width: 100%; max-width: none; margin-top: 38rpx; }
.topics-title { display: block; margin: 0 0 14rpx; color: var(--theme-text-placeholder); font-size: 21rpx; }
.topic-btn { position: relative; min-height: 84rpx; margin-bottom: 12rpx; padding: 19rpx 48rpx 19rpx 18rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-bg); box-shadow: none; box-sizing: border-box; }
.topic-btn::after { position: absolute; top: 50%; right: 18rpx; color: var(--theme-primary); content: '›'; font-size: 31rpx; font-weight: 300; transform: translateY(-50%); }
.topic-icon { width: 40rpx; margin-right: 14rpx; font-size: 31rpx; text-align: center; }
.topic-text { color: var(--theme-text-primary); font-size: 25rpx; line-height: 1.45; }
.message-row { margin-bottom: 24rpx; }
.bubble { max-width: 78%; padding: 20rpx 24rpx; border-radius: 8rpx; }
.ai-bubble { border: 1rpx solid var(--theme-border); box-shadow: none; }
.user-bubble { border-radius: 8rpx; }
.chat-bottom-wrapper { border-top: 1rpx solid var(--theme-border); background: var(--theme-surface); }
.chat-model-row { display: flex; align-items: center; justify-content: space-between; padding: 12rpx 26rpx 0; }
.chat-model-label { color: var(--theme-text-placeholder); font-size: 21rpx; }
.chat-model-picker { padding: 7rpx 12rpx; border: 1rpx solid var(--theme-border); border-radius: 6rpx; color: var(--theme-primary); font-size: 22rpx; }
.chat-model-arrow { margin-left: 6rpx; color: var(--theme-text-placeholder); }
.chat-input-bar { gap: 14rpx; padding: 18rpx 26rpx; }
.reference-actions { display: flex; gap: 8rpx; flex: 0 0 auto; }
.quote-action-btn, .event-quote-action-btn { display: flex; width: 62rpx; height: 62rpx; flex: 0 0 62rpx; align-items: center; justify-content: center; border: 1rpx solid var(--theme-border); border-radius: 6rpx; color: var(--theme-primary); }
.quote-action-icon { font-size: 34rpx; line-height: 1; }
.chat-input { height: 64rpx; padding: 0 20rpx; border-radius: 6rpx; background: var(--theme-bg); font-size: 26rpx; }
.send-btn { height: 64rpx; padding: 0 24rpx; border-radius: 6rpx; font-size: 25rpx; line-height: 64rpx; }
</style>

<style scoped>
.chat-container {
  padding-right: 32rpx;
  padding-left: 32rpx;
}

.chat-task-context {
  display: flex;
  align-items: center;
  gap: 10rpx;
  overflow: hidden;
  padding: 0 32rpx 12rpx;
  color: var(--theme-text-secondary);
  font-size: 22rpx;
  line-height: 1.4;
}

.chat-task-label,
.chat-task-detail {
  color: var(--theme-text-secondary);
}

.chat-task-name {
  color: var(--theme-primary);
  font-weight: 600;
  flex-shrink: 0;
}

.chat-task-detail {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.welcome-section {
  margin-top: 26rpx;
  padding: 38rpx 22rpx 24rpx;
  border-radius: var(--theme-radius-md);
  background: var(--theme-surface);
  box-shadow: var(--theme-shadow-panel);
}

.welcome-mark {
  width: 74rpx;
  height: 74rpx;
  margin-bottom: 16rpx;
  border-color: var(--theme-border);
  background: color-mix(in oklab, var(--theme-primary) 6%, var(--theme-surface));
}

.welcome-avatar {
  width: 62rpx;
  height: 62rpx;
}

.welcome-brand {
  color: var(--theme-text-primary);
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 39rpx;
  font-weight: 700;
}

.welcome-copy {
  font-size: 23rpx;
}

.welcome-topics {
  margin-top: 30rpx;
}

.topic-btn {
  min-height: 76rpx;
  margin-bottom: 10rpx;
  border-radius: var(--theme-radius-sm);
  background: var(--theme-bg);
}

.topic-text {
  font-size: 24rpx;
}

.message-row {
  margin-bottom: 22rpx;
}

.avatar {
  width: 56rpx;
  height: 56rpx;
}

.ai-avatar {
  margin-right: 14rpx;
}

.user-avatar {
  margin-left: 14rpx;
}

.bubble {
  max-width: 80%;
  padding: 18rpx 22rpx;
  border-radius: var(--theme-radius-md);
  font-size: 27rpx;
  line-height: 1.65;
}

.ai-bubble {
  border-top-left-radius: var(--theme-radius-sm);
  background: var(--theme-surface);
  box-shadow: var(--theme-shadow-panel);
}

.user-bubble {
  border-bottom-right-radius: var(--theme-radius-sm);
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
}

.message-text {
  font-size: 26rpx;
  line-height: 1.7;
}

.chat-bottom-wrapper {
  bottom: calc(96rpx + env(safe-area-inset-bottom));
  background: var(--theme-surface);
  box-shadow: 0 -1rpx 8rpx color-mix(in oklab, var(--theme-primary) 5%, transparent);
}

.quote-preview-bar {
  border-bottom-color: var(--theme-border);
  background: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
}

.quote-close {
  border-radius: var(--theme-radius-sm);
  background: var(--theme-surface-hover);
}

.chat-input-bar {
  gap: 10rpx;
  padding: 14rpx 26rpx;
}

.quote-action-btn,
.event-quote-action-btn,
.chat-input,
.send-btn {
  border-radius: var(--theme-radius-sm);
}

.chat-input {
  border-color: var(--theme-border);
  font-size: 25rpx;
}

.send-btn {
  min-width: 94rpx;
  padding: 0 20rpx;
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
}

.diary-selector-sheet {
  border-radius: var(--theme-radius-lg) var(--theme-radius-lg) 0 0;
  background: var(--theme-surface);
  box-shadow: var(--theme-shadow-dialog);
}

.sheet-diary-item {
  margin-bottom: 10rpx;
  padding: 20rpx;
  border-radius: var(--theme-radius-sm);
  border-color: var(--theme-border);
  background: var(--theme-bg);
  box-shadow: none;
}

.sheet-diary-content {
  font-size: 25rpx;
}

.persona-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1000000;
  display: flex;
  align-items: flex-end;
  background: var(--theme-overlay);
}

.persona-sheet {
  width: 100%;
  height: calc(100vh - 48rpx);
  max-height: none;
  border-radius: var(--theme-radius-lg) var(--theme-radius-lg) 0 0;
  background: var(--theme-surface);
  box-shadow: var(--theme-shadow-dialog);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  padding-bottom: env(safe-area-inset-bottom);
}

.persona-sheet-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  padding: 30rpx 32rpx 24rpx;
  border-bottom: 1rpx solid var(--theme-border);
}

.persona-sheet-title,
.persona-sheet-hint {
  display: block;
}

.persona-sheet-title {
  color: var(--theme-text-primary);
  font-size: 32rpx;
  font-weight: 650;
}

.persona-sheet-hint {
  margin-top: 8rpx;
  color: var(--theme-text-placeholder);
  font-size: 22rpx;
  line-height: 1.45;
}

.persona-sheet-content {
  flex: 1;
  min-height: 0;
  height: 0;
  max-height: none;
  padding: 8rpx 32rpx calc(96rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.persona-field-label {
  display: block;
  margin: 22rpx 0 12rpx;
  color: var(--theme-text-secondary);
  font-size: 24rpx;
  font-weight: 600;
}

.persona-picker-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: var(--theme-bg);
  color: var(--theme-text-primary);
  font-size: 26rpx;
  box-sizing: border-box;
}

.persona-picker-arrow {
  color: var(--theme-primary);
  font-size: 34rpx;
}

.persona-choice-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx 18rpx;
}

.persona-choice-item {
  display: flex;
  align-items: center;
  min-height: 54rpx;
  color: var(--theme-text-primary);
  font-size: 24rpx;
}

.persona-choice-item checkbox {
  transform: scale(.78);
}

.persona-textarea {
  display: block;
  width: 100%;
  min-height: 140rpx;
  padding: 18rpx 20rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: var(--theme-bg);
  color: var(--theme-text-primary);
  font-size: 25rpx;
  line-height: 1.55;
  box-sizing: border-box;
}

.persona-input {
  display: block;
  width: 100%;
  min-height: 76rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  border: 1px solid var(--theme-border);
  border-radius: 8rpx;
  background: var(--theme-bg);
  color: var(--theme-text-primary);
  font-size: 27rpx;
}

.persona-field-help {
  display: block;
  margin-top: 10rpx;
  color: var(--theme-text-secondary);
  font-size: 23rpx;
  line-height: 1.5;
}

.persona-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
}

.persona-save,
.persona-reset {
  flex: 1;
  height: 72rpx;
  margin: 0;
  border-radius: var(--theme-radius-sm);
  font-size: 25rpx;
  line-height: 72rpx;
}

.persona-save {
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
}

.persona-reset {
  border: 1rpx solid var(--theme-border);
  background: var(--theme-bg);
  color: var(--theme-text-secondary);
}

.persona-save::after,
.persona-reset::after {
  border: none;
}

.persona-save[disabled],
.persona-reset[disabled] {
  opacity: .55;
}

.persona-message {
  display: block;
  margin-top: 16rpx;
  color: var(--theme-primary);
  font-size: 23rpx;
  text-align: center;
}
</style>

