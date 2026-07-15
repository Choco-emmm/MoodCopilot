<template>
  <view class="chat-page" :style="localThemeStyle">
    <GlobalUI :tabIndex="2" />
    <scroll-view 
      class="chat-scroll" 
      scroll-y 
      :scroll-into-view="scrollToMessage"
      :scroll-with-animation="true"
    >
      <view class="sub-header" v-if="conversationId">
        <text class="current-session-title">陪伴</text>
        <view class="header-actions">
          <view class="history-btn new-chat-action" @click="createNewChat">
            <text>＋ 新对话</text>
          </view>
          <view class="history-btn" @click="showDrawer = true">
            <text>··· 历史</text>
          </view>
        </view>
      </view>

      <view class="chat-container">
        <!-- 侧边栏抽屉：历史对话 -->
        <view class="drawer-overlay" v-if="showDrawer" @click="showDrawer = false">
          <view class="drawer-content" @click.stop>
            <view class="drawer-header">
              <text class="drawer-title">所有对话</text>
              <button class="new-chat-btn" @click="createNewChat">＋ 新对话</button>
            </view>
            <scroll-view scroll-y class="drawer-list">
              <view 
                v-for="conv in conversations" 
                :key="conv.id" 
                class="conv-item"
                :class="{ active: conv.id === conversationId }"
                @click="switchConversation(conv.id)"
              >
                <text class="conv-title">{{ conv.title || '新对话' }}</text>
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
          <view class="system-message">
            <text>你可以随时和我聊聊今天的事。</text>
          </view>
          <view v-if="welcomeTopics.length > 0" class="welcome-topics">
            <text class="topics-title">或者试试这些话题：</text>
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
      <!-- 引用预览 -->
      <view v-if="selectedQuote" class="quote-preview-bar fade-in">
        <text class="quote-icon">❝</text>
        <text class="quote-text">{{ selectedQuote.text }}</text>
        <view class="quote-close" @click="clearQuote">×</view>
      </view>
      
      <view class="chat-input-bar">
        <view class="quote-action-btn hover-scale" @click="openDiarySelector">
          <text class="quote-action-icon">📖</text>
        </view>
        <input 
          class="chat-input" 
          v-model="inputContent" 
          placeholder="说点什么..." 
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
            <text class="sheet-diary-content">{{ diary.content }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue';
import { get, post, del, getFullUrl } from '@/utils/request';
import { parseMarkdown } from '@/utils/markdown';
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle, syncNavigationBarColor } from '@/stores/theme';
import { onShow } from '@dcloudio/uni-app';

const localThemeStyle = ref(themeStyle.value);

onShow(() => {
  localThemeStyle.value = themeStyle.value;
  syncNavigationBarColor();
  const pendingQuote = uni.getStorageSync('pendingQuote');
  if (pendingQuote) {
    selectedQuote.value = { text: pendingQuote };
    uni.removeStorageSync('pendingQuote');
  }
});

uni.$on('themeChanged', () => {
  localThemeStyle.value = themeStyle.value;
});

interface Message {
  role: 'user' | 'assistant';
  content: string;
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
const selectedQuote = ref<{text: string} | null>(null);

const showDiarySelector = ref(false);
const recentDiaries = ref<any[]>([]);
const loadingDiaries = ref(false);

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
  initConversation();
  fetchUserInfo();
});

const fetchUserInfo = async () => {
  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data) {
      userInfo.value = res.data.user;
      uni.setStorageSync('userInfo', res.data.user);
    }
  } catch (e) {
    console.error('获取用户信息失败', e);
  }
};

onShow(() => {
  const pendingQuote = uni.getStorageSync('pendingQuote');
  if (pendingQuote) {
    selectedQuote.value = { text: pendingQuote };
    uni.removeStorageSync('pendingQuote');
  }
});

const openDiarySelector = async () => {
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

const selectDiaryForQuote = (diary: any) => {
  selectedQuote.value = { text: diary.content };
  showDiarySelector.value = false;
};

const clearQuote = () => {
  selectedQuote.value = null;
};

const handleLongPress = (msg: Message) => {
  uni.showActionSheet({
    itemList: ['引用', '复制'],
    success: (res) => {
      if (res.tapIndex === 0) {
        selectedQuote.value = { text: msg.content };
      } else if (res.tapIndex === 1) {
        uni.setClipboardData({
          data: msg.content,
          success: () => uni.showToast({ title: '已复制', icon: 'none' })
        });
      }
    }
  });
};

const initConversation = async () => {
  try {
    const res = await get('/api/chat/conversations');
    if (res.code === 200 && res.data && res.data.length > 0) {
      conversations.value = res.data;
      // 如果有历史记录，并且是刚进页面，我们可以检查一下。但为了满足“默认是新话题页面”的需求，直接建新对话。
      createNewChat();
    } else {
      createNewChat();
    }
  } catch (e) {
    console.error('初始化会话失败', e);
  } finally {
    loadingInit.value = false;
  }
};

const fetchWelcomeTopics = async () => {
  const defaultTopics = [
    JSON.stringify({ icon: '🤔', text: '帮我复盘一下今天的情绪' }),
    JSON.stringify({ icon: '🌱', text: '我最近总是感到有些焦虑，该怎么办？' }),
    JSON.stringify({ icon: '📅', text: '和我聊聊接下来的计划吧' })
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
  try {
    const res = await post('/api/chat/conversations', { title: '新对话' });
    if (res.code === 200) {
      conversationId.value = res.data.id;
      messages.value = [];
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
};

const loadHistory = async () => {
  if (!conversationId.value) return;
  try {
    const res = await get(`/api/chat/conversations/${conversationId.value}/history`);
    if (res.code === 200 && res.data && res.data.messages) {
      messages.value = res.data.messages || [];
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
  sendMessage();
};

const sendMessage = async () => {
  if (!inputContent.value.trim() || isWaiting.value || !conversationId.value) return;

  const content = inputContent.value.trim();
  const currentQuote = selectedQuote.value ? selectedQuote.value.text : null;
  
  messages.value.push({ role: 'user', content });
  inputContent.value = '';
  selectedQuote.value = null;
  isWaiting.value = true;
  scrollToBottom('waiting');

  try {
    const references = currentQuote ? [currentQuote] : [];
    const res = await post(`/api/chat/conversations/${conversationId.value}/reply`, {
      message: content,
      references: references
    });
    
    if (res.code === 200) {
      messages.value.push({ role: 'assistant', content: res.data });
    } else {
      messages.value.push({ role: 'assistant', content: '抱歉，我现在有点走神，请稍后再试' });
    }
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '网络似乎出了点问题' });
  } finally {
    isWaiting.value = false;
    scrollToBottom();
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
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.03);
  display: flex;
  flex-direction: row;
  align-items: center;
  border: 1px solid rgba(var(--theme-primary-rgb), 0.1);
  transition: all 0.2s ease;
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
  color: #fff;
  margin-right: 16rpx;
  overflow: hidden;
}
.user-avatar {
  background-color: var(--theme-primary);
  color: #fff;
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
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.03);
  border-top-left-radius: 0;
}
.user-bubble {
  background-color: var(--theme-primary);
  color: #fff;
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
  border-top: 1px solid rgba(var(--theme-primary-rgb), 0.1);
  z-index: 100;
}

.quote-preview-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 32rpx;
  background-color: rgba(var(--theme-primary-rgb), 0.05);
  border-bottom: 1px dashed rgba(var(--theme-primary-rgb), 0.1);
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
  background: rgba(0,0,0,0.05);
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
  border: 1px solid rgba(var(--theme-primary-rgb), 0.1);
}
.send-btn {
  background-color: var(--theme-primary);
  color: #fff;
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
  background-color: rgba(var(--theme-surface-rgb, 255, 255, 255), 0.5);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(var(--theme-primary-rgb), 0.1);
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
  background: rgba(var(--theme-primary-rgb), 0.1);
  border-radius: 30rpx;
}

/* 抽屉层 */
.drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}
.drawer-content {
  width: 75vw;
  height: 100%;
  background-color: var(--theme-bg);
  box-shadow: -4rpx 0 20rpx rgba(0, 0, 0, 0.1);
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
  border-bottom: 1px solid rgba(var(--theme-primary-rgb), 0.1);
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
  color: #fff;
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
  border-bottom: 1px solid rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.conv-item.active {
  background-color: rgba(var(--theme-primary-rgb), 0.05);
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
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 99999;
  display: flex;
  justify-content: center;
  align-items: flex-end;
}

.diary-selector-sheet {
  width: 100%;
  height: 60vh;
  background-color: #F6F2EA;
  border-top-left-radius: 40rpx;
  border-top-right-radius: 40rpx;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.1);
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
  border-bottom: 1px solid rgba(0,0,0,0.05);
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
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.02);
  border: 1px solid rgba(var(--theme-primary-rgb), 0.05);
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
</style>
