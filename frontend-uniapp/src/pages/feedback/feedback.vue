<template>
  <view class="feedback-page" :style="globalThemeStyle">
    <GlobalUI />

    <view class="page-header">
      <text class="page-eyebrow">FEEDBACK</text>
      <text class="page-title">意见反馈</text>
      <text class="page-desc">仅你与管理员可见，支持双向回复沟通。</text>
    </view>

    <view v-if="isAdmin" class="tab-bar">
      <view
        class="tab-item"
        :class="{ active: activeTab === 'mine' }"
        @click="switchTab('mine')"
      >
        <text>我的反馈</text>
      </view>
      <view
        class="tab-item"
        :class="{ active: activeTab === 'admin' }"
        @click="switchTab('admin')"
      >
        <text>全部反馈 (管理员)</text>
      </view>
    </view>

    <!-- 提交新反馈 -->
    <view v-if="activeTab === 'mine'" class="composer-panel">
      <view class="composer-head">
        <text class="section-heading">写下新反馈</text>
        <text class="word-count">{{ content.length }}/1000</text>
      </view>
      <textarea
        class="feedback-input"
        v-model="content"
        placeholder="写下你的想法、建议或是遇到的问题..."
        :maxlength="1000"
        cursor-spacing="20"
      ></textarea>
      <view class="composer-foot">
        <text class="composer-note">提交及后续回复都会提醒管理员</text>
        <button
          class="submit-btn"
          :class="{ disabled: !content.trim() || isSubmitting }"
          :disabled="!content.trim() || isSubmitting"
          @click="submitFeedback"
        >
          {{ isSubmitting ? '提交中...' : '提交反馈' }}
        </button>
      </view>
    </view>

    <!-- 管理员状态筛选 -->
    <view v-if="activeTab === 'admin' && isAdmin" class="filter-bar">
      <view
        v-for="opt in statusFilters"
        :key="opt.value"
        class="filter-pill"
        :class="{ active: statusFilter === opt.value }"
        @click="selectStatusFilter(opt.value)"
      >
        <text>{{ opt.label }}</text>
      </view>
    </view>

    <!-- 反馈列表标题 -->
    <view class="list-header">
      <text class="section-heading">
        {{ activeTab === 'admin' ? '全部用户反馈' : '我的反馈记录' }} ({{ total }})
      </text>
      <text class="refresh-action" @click="loadList(true)">刷新</text>
    </view>

    <view v-if="loading && !items.length" class="state-box">
      <text class="state-text">加载中...</text>
    </view>

    <view v-else-if="!items.length" class="state-box">
      <text class="state-text">
        {{ activeTab === 'admin' ? '暂无匹配的用户反馈' : '还没有提交过反馈' }}
      </text>
    </view>

    <view v-else class="feedback-list">
      <view
        v-for="item in items"
        :key="item.id"
        class="feedback-item"
        :class="{ highlighted: highlightId === item.id }"
      >
        <view class="item-head">
          <view class="item-author-wrap">
            <text class="item-author">{{ item.userName }}</text>
            <text class="status-tag" :class="`status-${(item.status || 'PENDING').toLowerCase()}`">
              {{ statusLabel(item.status) }}
            </text>
          </view>
          <text class="item-time">{{ formatTime(item.createdAt) }}</text>
        </view>

        <text class="item-content">{{ item.content }}</text>

        <view v-if="activeTab === 'admin' && isAdmin" class="admin-status-actions">
          <text
            v-if="item.status !== 'READ'"
            class="action-link"
            @click="updateStatus(item, 'READ')"
          >标记跟进中</text>
          <text
            v-if="item.status !== 'RESOLVED'"
            class="action-link action-primary"
            @click="updateStatus(item, 'RESOLVED')"
          >标记已解决</text>
          <text
            v-if="item.status === 'RESOLVED'"
            class="action-link"
            @click="updateStatus(item, 'PENDING')"
          >重新打开</text>
        </view>

        <!-- 评论式回复区 -->
        <view class="replies-section">
          <view class="replies-bar">
            <text class="replies-count">回复 ({{ item.replyCount ?? countReplies(item.replies) }})</text>
            <text class="action-link" @click="openDirectReply(item.id)">
              {{ activeComposerSuggestionId === item.id && !replyTarget ? '收起输入' : '写回复' }}
            </text>
          </view>

          <view v-if="item.replies && item.replies.length" class="reply-threads">
            <view v-for="reply in item.replies" :key="reply.id" class="reply-thread">
              <view class="reply-main">
                <view class="reply-meta">
                  <text class="reply-author">{{ reply.authorName }}</text>
                  <text v-if="reply.authorRole === 'ADMIN'" class="role-badge role-admin">管理员</text>
                  <text v-else-if="reply.authorUserId === item.userId" class="role-badge">反馈人</text>
                  <text v-if="reply.replyToUserName" class="reply-to">→ 回复 {{ reply.replyToUserName }}</text>
                  <text class="reply-time">{{ formatTime(reply.createdAt) }}</text>
                </view>
                <text class="reply-body">{{ reply.content }}</text>
                <view class="reply-foot">
                  <text
                    v-if="canDeleteReply(reply)"
                    class="reply-action reply-action-danger"
                    @click="confirmDeleteReply(item.id, reply.id)"
                  >删除</text>
                  <text
                    class="reply-action"
                    @click="startNestedReply(item.id, reply.id, reply.authorName)"
                  >回复</text>
                </view>
              </view>

              <view v-if="reply.replies && reply.replies.length" class="nested-replies">
                <view v-for="child in reply.replies" :key="child.id" class="nested-reply-item">
                  <view class="reply-meta">
                    <text class="reply-author">{{ child.authorName }}</text>
                    <text v-if="child.authorRole === 'ADMIN'" class="role-badge role-admin">管理员</text>
                    <text v-else-if="child.authorUserId === item.userId" class="role-badge">反馈人</text>
                    <text v-if="child.replyToUserName" class="reply-to">→ 回复 {{ child.replyToUserName }}</text>
                    <text class="reply-time">{{ formatTime(child.createdAt) }}</text>
                  </view>
                  <text class="reply-body">{{ child.content }}</text>
                  <view class="reply-foot">
                    <text
                      v-if="canDeleteReply(child)"
                      class="reply-action reply-action-danger"
                      @click="confirmDeleteReply(item.id, child.id)"
                    >删除</text>
                    <text
                      class="reply-action"
                      @click="startNestedReply(item.id, child.id, child.authorName)"
                    >回复</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
          <text v-else class="no-replies-hint">
            {{ activeTab === 'admin' ? '暂未回复这条反馈' : '暂未收到回复，可点击「写回复」补充说明' }}
          </text>

          <!-- 回复输入条 -->
          <view v-if="activeComposerSuggestionId === item.id" class="reply-composer">
            <view v-if="replyTarget" class="reply-target-bar">
              <text class="reply-target-text">正在回复 {{ replyTarget.targetName }}</text>
              <text class="action-link" @click="cancelReplyTarget">取消</text>
            </view>
            <view class="reply-input-row">
              <input
                class="reply-input"
                v-model="replyDraft"
                :placeholder="replyTarget ? `回复 ${replyTarget.targetName}...` : '写下你的回复...'"
                :maxlength="1000"
                confirm-type="send"
                @confirm="sendReply(item.id)"
              />
              <button
                class="reply-send-btn"
                :class="{ disabled: !replyDraft.trim() || sendingReply }"
                :disabled="!replyDraft.trim() || sendingReply"
                @click="sendReply(item.id)"
              >
                {{ sendingReply ? '...' : '发送' }}
              </button>
            </view>
          </view>
        </view>
      </view>

      <view v-if="hasMore" class="load-more-wrap">
        <button class="load-more-btn" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { del, get, post, put } from '@/utils/request';
import { currentUser, fetchCurrentUser } from '@/stores/user';
import GlobalUI from '@/components/GlobalUI.vue';

interface SuggestionReply {
  id: number;
  suggestionId: number;
  parentReplyId: number | null;
  rootReplyId: number | null;
  authorUserId: number;
  authorName: string;
  authorAvatar: string | null;
  authorRole: string;
  replyToUserName: string | null;
  content: string;
  createdAt: string;
  replies: SuggestionReply[];
}

interface SuggestionItem {
  id: number;
  userId: number;
  userName: string;
  userAvatar: string | null;
  content: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  replyCount: number;
  replies: SuggestionReply[];
}

const statusFilters = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待回复' },
  { value: 'READ', label: '跟进中' },
  { value: 'RESOLVED', label: '已解决' },
];

const content = ref('');
const isSubmitting = ref(false);
const activeTab = ref<'mine' | 'admin'>('mine');
const statusFilter = ref('ALL');

const items = ref<SuggestionItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(false);
const loadingMore = ref(false);
const highlightId = ref<number | null>(null);

const activeComposerSuggestionId = ref<number | null>(null);
const replyTarget = ref<{ parentReplyId: number; targetName: string } | null>(null);
const replyDraft = ref('');
const sendingReply = ref(false);

const isAdmin = computed(() => {
  const role = String(currentUser.value?.role || '').toUpperCase();
  return role === 'ADMIN' || role === 'ROLE_ADMIN';
});

const currentUserId = computed<number | null>(() => {
  const id = Number(currentUser.value?.userId ?? currentUser.value?.id);
  return Number.isFinite(id) && id > 0 ? id : null;
});

const hasMore = computed(() => items.value.length < total.value);

onLoad(async (options) => {
  if (!currentUser.value) {
    await fetchCurrentUser();
  }
  const targetId = Number(options?.suggestionId || options?.id);
  if (Number.isFinite(targetId) && targetId > 0) {
    highlightId.value = targetId;
    activeComposerSuggestionId.value = targetId;
    try {
      const detailRes = await get<SuggestionItem>(`/api/suggestions/${targetId}`);
      if (detailRes.code === 200 && detailRes.data) {
        if (isAdmin.value && currentUserId.value && detailRes.data.userId !== currentUserId.value) {
          activeTab.value = 'admin';
        }
      }
    } catch {
      // Fall back to regular list
    }
  }
  await loadList(true);
  if (highlightId.value && !items.value.some((i) => i.id === highlightId.value)) {
    try {
      const detailRes = await get<SuggestionItem>(`/api/suggestions/${highlightId.value}`);
      if (detailRes.code === 200 && detailRes.data) {
        items.value = [detailRes.data, ...items.value];
      }
    } catch {
      // Ignore
    }
  }
});

const loadList = async (reset = true) => {
  if (reset) {
    loading.value = true;
    page.value = 1;
  } else {
    if (loadingMore.value || !hasMore.value) return;
    loadingMore.value = true;
  }

  try {
    const nextPage = reset ? 1 : page.value + 1;
    const isAdminTab = activeTab.value === 'admin' && isAdmin.value;
    const url = isAdminTab ? '/api/suggestions/admin' : '/api/suggestions/mine';
    const params: Record<string, any> = { page: nextPage, size: pageSize };
    if (isAdminTab && statusFilter.value !== 'ALL') {
      params.status = statusFilter.value;
    }

    const res = await get<{ total: number; items: SuggestionItem[] }>(url, params);
    if (res.code === 200 && res.data) {
      const fetched = res.data.items || [];
      total.value = Number(res.data.total || 0);
      if (reset) {
        items.value = fetched;
        page.value = 1;
      } else {
        const existing = new Set(items.value.map((i) => i.id));
        items.value = [...items.value, ...fetched.filter((i) => !existing.has(i.id))];
        page.value = nextPage;
      }
    }
  } catch (e) {
    console.error('加载反馈失败', e);
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

const loadMore = () => {
  void loadList(false);
};

const switchTab = (tab: 'mine' | 'admin') => {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  activeComposerSuggestionId.value = null;
  replyTarget.value = null;
  replyDraft.value = '';
  void loadList(true);
};

const selectStatusFilter = (status: string) => {
  if (statusFilter.value === status) return;
  statusFilter.value = status;
  void loadList(true);
};

const submitFeedback = async () => {
  const trimmed = content.value.trim();
  if (!trimmed || isSubmitting.value) return;
  isSubmitting.value = true;

  try {
    const res = await post<SuggestionItem>('/api/suggestions', { content: trimmed });
    if (res.code === 200) {
      content.value = '';
      if (res.data) {
        items.value = [res.data, ...items.value.filter((i) => i.id !== res.data.id)];
        total.value += 1;
      } else {
        await loadList(true);
      }
      uni.showToast({ title: '反馈已提交，感谢支持！', icon: 'success' });
    } else {
      uni.showToast({ title: res.message || '提交失败', icon: 'none' });
    }
  } catch (e) {
    console.error('提交反馈失败', e);
  } finally {
    isSubmitting.value = false;
  }
};

const openDirectReply = (suggestionId: number) => {
  if (activeComposerSuggestionId.value === suggestionId && !replyTarget.value) {
    activeComposerSuggestionId.value = null;
    replyDraft.value = '';
    return;
  }
  activeComposerSuggestionId.value = suggestionId;
  replyTarget.value = null;
  replyDraft.value = '';
};

const startNestedReply = (suggestionId: number, parentReplyId: number, targetName: string) => {
  if (
    activeComposerSuggestionId.value === suggestionId
    && replyTarget.value?.parentReplyId === parentReplyId
  ) {
    activeComposerSuggestionId.value = null;
    replyTarget.value = null;
    replyDraft.value = '';
    return;
  }
  activeComposerSuggestionId.value = suggestionId;
  replyTarget.value = { parentReplyId, targetName };
  replyDraft.value = '';
};

const cancelReplyTarget = () => {
  replyTarget.value = null;
};

const sendReply = async (suggestionId: number) => {
  const trimmed = replyDraft.value.trim();
  if (!trimmed || sendingReply.value) return;

  sendingReply.value = true;
  try {
    const payload: Record<string, any> = { content: trimmed };
    if (replyTarget.value?.parentReplyId) {
      payload.parentReplyId = replyTarget.value.parentReplyId;
    }
    const res = await post<SuggestionItem>(`/api/suggestions/${suggestionId}/replies`, payload);
    if (res.code === 200 && res.data) {
      replaceItem(res.data);
      replyDraft.value = '';
      replyTarget.value = null;
      uni.showToast({ title: '回复已发送', icon: 'success' });
    }
  } catch (e) {
    console.error('发送回复失败', e);
  } finally {
    sendingReply.value = false;
  }
};

const canDeleteReply = (reply: SuggestionReply) => {
  if (isAdmin.value) return true;
  return currentUserId.value != null && reply.authorUserId === currentUserId.value;
};

const confirmDeleteReply = (suggestionId: number, replyId: number) => {
  uni.showModal({
    title: '删除回复',
    content: '确定要删除这条回复吗？',
    success: async (modalRes) => {
      if (!modalRes.confirm) return;
      try {
        const res = await del<SuggestionItem>(`/api/suggestions/${suggestionId}/replies/${replyId}`);
        if (res.code === 200 && res.data) {
          replaceItem(res.data);
          uni.showToast({ title: '已删除', icon: 'success' });
        }
      } catch (e) {
        console.error('删除回复失败', e);
      }
    },
  });
};

const updateStatus = async (item: SuggestionItem, status: string) => {
  try {
    const res = await put<SuggestionItem>(`/api/suggestions/${item.id}/status`, { status });
    if (res.code === 200 && res.data) {
      replaceItem(res.data);
      uni.showToast({ title: `已更新为${statusLabel(status)}`, icon: 'success' });
    }
  } catch (e) {
    console.error('更新状态失败', e);
  }
};

const replaceItem = (updated: SuggestionItem) => {
  const idx = items.value.findIndex((i) => i.id === updated.id);
  if (idx >= 0) {
    items.value.splice(idx, 1, updated);
  }
};

const statusLabel = (status?: string) => {
  switch ((status || '').toUpperCase()) {
    case 'RESOLVED': return '已解决';
    case 'READ': return '跟进中';
    default: return '待回复';
  }
};

const countReplies = (list?: SuggestionReply[]) => {
  if (!list || !list.length) return 0;
  return list.reduce((sum, item) => sum + 1 + (item.replies?.length || 0), 0);
};

const formatTime = (value?: string) => {
  if (!value) return '';
  const date = new Date(value.replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return value;
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  return `${m}-${d} ${hh}:${mm}`;
};
</script>

<style scoped>
.feedback-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 28rpx var(--theme-page-padding) calc(80rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.page-header {
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid var(--theme-border);
}

.page-eyebrow {
  display: block;
  font-size: 20rpx;
  letter-spacing: 4rpx;
  color: var(--theme-text-placeholder);
  margin-bottom: 8rpx;
}

.page-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 44rpx;
  font-weight: 700;
  color: var(--theme-text-primary);
  margin-bottom: 10rpx;
}

.page-desc {
  display: block;
  font-size: 26rpx;
  color: var(--theme-text-secondary);
  line-height: 1.5;
}

.tab-bar {
  display: flex;
  gap: 12rpx;
  margin-top: 24rpx;
  padding: 6rpx;
  border-radius: var(--theme-radius-md);
  background-color: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 14rpx 0;
  border-radius: var(--theme-radius-sm);
  font-size: 26rpx;
  color: var(--theme-text-secondary);
}

.tab-item.active {
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
  font-weight: 600;
}

.composer-panel {
  margin-top: 24rpx;
  padding: 24rpx;
  background-color: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-md);
}

.composer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}

.section-heading {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.word-count {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
}

.feedback-input {
  width: 100%;
  height: 180rpx;
  font-size: 28rpx;
  line-height: 1.6;
  color: var(--theme-text-primary);
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--theme-border);
}

.composer-note {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
  flex: 1;
}

.submit-btn {
  margin: 0;
  padding: 0 28rpx;
  height: 64rpx;
  line-height: 64rpx;
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
  border-radius: var(--theme-radius-sm);
  font-size: 26rpx;
  font-weight: 600;
}

.submit-btn::after {
  border: 0;
}

.submit-btn.disabled {
  opacity: 0.5;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 24rpx;
}

.filter-pill {
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--theme-border);
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  background-color: transparent;
}

.filter-pill.active {
  border-color: var(--theme-primary);
  background-color: color-mix(in oklab, var(--theme-primary) 12%, var(--theme-surface));
  color: var(--theme-primary);
  font-weight: 600;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 32rpx;
  padding-bottom: 14rpx;
  border-bottom: 1rpx solid var(--theme-border);
}

.refresh-action {
  font-size: 24rpx;
  color: var(--theme-primary);
}

.state-box {
  padding: 72rpx 0;
  text-align: center;
}

.state-text {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
}

.feedback-list {
  display: flex;
  flex-direction: column;
}

.feedback-item {
  padding: 28rpx 0;
  border-bottom: 1rpx solid var(--theme-border);
}

.feedback-item.highlighted {
  margin: 16rpx -16rpx;
  padding: 24rpx 16rpx;
  border-radius: var(--theme-radius-md);
  background-color: color-mix(in oklab, var(--theme-primary) 6%, var(--theme-surface));
}

.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12rpx;
}

.item-author-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.item-author {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.status-tag {
  font-size: 20rpx;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--theme-border);
  color: var(--theme-text-secondary);
  background-color: var(--theme-surface);
}

.status-tag.status-pending {
  border-color: var(--theme-accent);
  color: var(--theme-accent);
}

.status-tag.status-read,
.status-tag.status-resolved {
  border-color: var(--theme-primary);
  color: var(--theme-primary);
}

.item-time {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
}

.item-content {
  display: block;
  margin-top: 14rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--theme-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
}

.admin-status-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 14rpx;
}

.action-link {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
}

.action-primary {
  color: var(--theme-primary);
  font-weight: 600;
}

.replies-section {
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--theme-border);
}

.replies-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.replies-count {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
  letter-spacing: 1rpx;
}

.reply-threads {
  display: flex;
  flex-direction: column;
}

.reply-thread {
  padding: 14rpx 0;
  border-bottom: 1rpx solid var(--theme-border);
}

.reply-thread:last-child {
  border-bottom: 0;
}

.reply-meta {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10rpx;
}

.reply-author {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.role-badge {
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
  border: 1rpx solid var(--theme-border);
  color: var(--theme-text-secondary);
  background-color: var(--theme-surface);
}

.role-badge.role-admin {
  border-color: var(--theme-primary);
  color: var(--theme-primary);
  background-color: color-mix(in oklab, var(--theme-primary) 10%, var(--theme-surface));
}

.reply-to {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
}

.reply-time {
  font-size: 20rpx;
  color: var(--theme-text-placeholder);
}

.reply-body {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  line-height: 1.65;
  color: var(--theme-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
}

.reply-foot {
  display: flex;
  justify-content: flex-end;
  gap: 24rpx;
  margin-top: 8rpx;
}

.reply-action {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
}

.reply-action-danger {
  color: var(--theme-accent);
}

.nested-replies {
  margin-top: 12rpx;
  margin-left: 20rpx;
  padding-left: 20rpx;
  border-left: 2rpx solid var(--theme-border);
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.no-replies-hint {
  display: block;
  padding: 8rpx 0;
  font-size: 24rpx;
  color: var(--theme-text-placeholder);
}

.reply-composer {
  margin-top: 16rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid var(--theme-border);
}

.reply-target-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10rpx;
}

.reply-target-text {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
}

.reply-input-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.reply-input {
  flex: 1;
  height: 68rpx;
  padding: 0 18rpx;
  font-size: 26rpx;
  color: var(--theme-text-primary);
  background-color: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
}

.reply-send-btn {
  margin: 0;
  padding: 0 24rpx;
  height: 68rpx;
  line-height: 68rpx;
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
  border-radius: var(--theme-radius-sm);
  font-size: 24rpx;
  font-weight: 600;
}

.reply-send-btn::after {
  border: 0;
}

.reply-send-btn.disabled {
  opacity: 0.5;
}

.load-more-wrap {
  padding: 28rpx 0;
  text-align: center;
}

.load-more-btn {
  display: inline-block;
  padding: 0 36rpx;
  height: 64rpx;
  line-height: 64rpx;
  background-color: transparent;
  color: var(--theme-text-secondary);
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  font-size: 24rpx;
}

.load-more-btn::after {
  border: 0;
}
</style>
