<template>
  <main class="app-shell">
    <AppHeader />

    <section class="announcement-admin">
      <header class="page-heading">
        <div>
          <p class="eyebrow">ADMIN</p>
          <h2>全局公告</h2>
          <p>发布后，小程序会将新版本展示给每位用户一次。</p>
        </div>
        <div v-if="current" class="publication-meta">
          <span>当前版本 v{{ current.version }}</span>
          <span>{{ formatTime(current.publishedAt) }}</span>
        </div>
      </header>

      <div v-if="!auth.isAdmin" class="empty-state">当前账号没有公告管理权限。</div>

      <template v-else>
        <div class="editor-layout">
          <section class="editor-pane">
            <label for="announcement-title">公告标题</label>
            <n-input id="announcement-title" v-model:value="title" :maxlength="60" show-count placeholder="例如：本周服务更新说明" />

            <label for="announcement-content">公告正文</label>
            <n-input
              id="announcement-content"
              v-model:value="content"
              type="textarea"
              :maxlength="2000"
              show-count
              :autosize="{ minRows: 12, maxRows: 22 }"
              placeholder="支持纯文本和换行，不支持富文本。"
            />

            <div class="editor-actions">
              <n-button secondary :disabled="!canPreview" @click="showPreview = true">预览</n-button>
              <n-button type="primary" :disabled="!canPublish" :loading="publishing" @click="confirmPublish">发布新版本</n-button>
            </div>
          </section>

          <aside class="current-pane">
            <p class="pane-label">已发布内容</p>
            <template v-if="current">
              <h3>{{ current.title }}</h3>
              <p class="current-content">{{ current.content }}</p>
              <p class="publisher">发布人：{{ current.publishedByDisplayName || `用户 #${current.publishedByUserId}` }}</p>
            </template>
            <p v-else class="no-announcement">尚未发布公告。</p>
          </aside>
        </div>
      </template>
    </section>

    <n-modal v-model:show="showPreview" preset="card" title="小程序公告预览" class="preview-modal" :bordered="false">
      <div class="mini-preview">
        <button class="preview-close" aria-label="关闭预览" @click="showPreview = false">×</button>
        <p class="preview-kicker">MOODCOPILOT</p>
        <h3>{{ title || '公告标题' }}</h3>
        <p class="preview-content">{{ content || '公告正文会显示在这里。' }}</p>
      </div>
    </n-modal>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NInput, NModal, useDialog, useMessage } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { announcementApi, type Announcement } from '../api/announcement'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const dialog = useDialog()
const message = useMessage()
const current = ref<Announcement | null>(null)
const title = ref('')
const content = ref('')
const showPreview = ref(false)
const publishing = ref(false)

const canPreview = computed(() => Boolean(title.value.trim() || content.value.trim()))
const canPublish = computed(() => Boolean(title.value.trim() && content.value.trim()))

onMounted(async () => {
  await auth.fetchProfile()
  if (!auth.isAdmin) return
  const response = await announcementApi.current()
  current.value = response.data.data
})

function confirmPublish() {
  if (!canPublish.value || publishing.value) return
  dialog.warning({
    title: '发布新版本',
    content: '发布后所有小程序用户会看到这条新公告一次，当前版本会被新版本替代。',
    positiveText: '确认发布',
    negativeText: '再检查一下',
    onPositiveClick: publish,
  })
}

async function publish() {
  publishing.value = true
  try {
    const response = await announcementApi.publish(title.value.trim(), content.value.trim())
    const published = response.data.data
    current.value = published
    title.value = ''
    content.value = ''
    showPreview.value = false
    message.success(`公告 v${published.version} 已发布`)
  } finally {
    publishing.value = false
  }
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>

<style scoped>
.announcement-admin { max-width: 1040px; margin: 0 auto; padding: var(--pad, 24px) 16px 96px; }
.page-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 24px; margin-bottom: 28px; }
.page-heading h2 { margin: 4px 0 8px; font-family: var(--font-display); font-size: 28px; color: var(--color-text); }
.page-heading p:not(.eyebrow) { margin: 0; color: var(--color-text-secondary); font-size: 14px; }
.eyebrow, .pane-label, .preview-kicker { margin: 0; font-size: 11px; font-weight: 700; letter-spacing: .12em; color: var(--color-accent); }
.publication-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; color: var(--color-text-secondary); font-size: 12px; white-space: nowrap; }
.editor-layout { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(260px, .8fr); gap: 24px; align-items: start; }
.editor-pane, .current-pane { padding: 22px; border: 1px solid var(--color-border); background: var(--color-surface); border-radius: 8px; }
.editor-pane { display: flex; flex-direction: column; gap: 10px; }
.editor-pane label { margin-top: 6px; font-size: 13px; font-weight: 700; color: var(--color-text); }
.editor-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 12px; }
.current-pane h3 { margin: 12px 0 10px; font-family: var(--font-display); font-size: 18px; color: var(--color-text); }
.current-content, .preview-content { margin: 0; color: var(--color-text-secondary); font-size: 14px; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
.publisher, .no-announcement { margin: 18px 0 0; color: var(--color-text-secondary); font-size: 12px; }
.preview-modal { width: min(400px, calc(100vw - 32px)); }
.mini-preview { position: relative; padding: 26px 22px 24px; background: var(--color-surface); border-top: 3px solid var(--color-accent); }
.mini-preview h3 { margin: 10px 36px 18px 0; color: var(--color-text); font-family: var(--font-display); font-size: 21px; line-height: 1.35; }
.preview-close { position: absolute; top: 18px; right: 18px; width: 28px; height: 28px; padding: 0; border: 0; border-radius: 50%; background: var(--color-bg); color: var(--color-text-secondary); font-size: 21px; line-height: 24px; cursor: pointer; }
@media (max-width: 720px) { .page-heading { flex-direction: column; gap: 10px; } .publication-meta { align-items: flex-start; } .editor-layout { grid-template-columns: 1fr; } }
</style>
