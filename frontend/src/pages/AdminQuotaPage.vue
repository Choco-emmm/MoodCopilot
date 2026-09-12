<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div class="title-area">
        <p class="eyebrow">ADMIN</p>
        <h1>AI 限额与说明</h1>
      </div>
      <div class="admin-actions">
        <n-button type="primary" @click="saveConfig" :loading="saving">保存配置</n-button>
      </div>
    </div>

    <div v-if="!auth.isAdmin" class="empty-state">当前账号没有管理员权限。</div>
    
    <div v-else class="admin-quota-content">
      <n-spin :show="loading">
        <div class="config-section">
          <h2>API 说明配置</h2>
          <div class="labels-grid">
            <div v-for="key in apiKeys" :key="key" class="label-item">
              <span class="label-key">{{ key }}</span>
              <n-input v-model:value="labels[key]" placeholder="请输入中文说明" />
            </div>
          </div>
        </div>

        <div class="config-section">
          <h2>等级配额矩阵</h2>
          <p class="section-desc">行表示等级（Pro, Lv.1 - Lv.6），列表示不同的 API 操作限制。</p>
          <div class="table-container">
            <table class="quota-table">
              <thead>
                <tr>
                  <th>等级</th>
                  <th v-for="key in apiKeys" :key="key">{{ labels[key] || key }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rIndex) in quotaMatrix" :key="rIndex">
                  <td class="level-col">{{ rIndex === 0 ? 'Pro' : 'Lv.' + rIndex }}</td>
                  <td v-for="(val, cIndex) in row" :key="cIndex">
                    <n-input-number v-model:value="quotaMatrix[rIndex][cIndex]" :min="0" :step="1" size="small" />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </n-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { adminApi } from '../api'
import { useAuthStore } from '../store/auth'

const auth = useAuthStore()
const message = useMessage()

const loading = ref(true)
const saving = ref(false)

const apiKeys = [
  'CHAT_FLASH', 'CHAT_PRO', 'DIARY_FLASH', 'DIARY_PRO',
  'CHAPTER_CONSOLIDATION', 'RESONANCE', 'REPORT',
  'IMAGE_UPLOAD', 'IMAGE_ANALYSIS'
]

const labels = ref<Record<string, string>>({})
const quotaMatrix = ref<number[][]>([])

onMounted(() => {
  if (auth.isAdmin) {
    loadConfig()
  }
})

async function loadConfig() {
  loading.value = true
  try {
    const res = await adminApi.aiQuota()
    if (res.data) {
      labels.value = res.data.labels || {}
      quotaMatrix.value = res.data.quotaMatrix || []
    }
  } catch (err: any) {
    message.error('获取配置失败: ' + (err.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  saving.value = true
  try {
    await adminApi.updateAiQuota({
      quotaMatrix: quotaMatrix.value,
      labels: labels.value
    })
    message.success('保存配置成功')
  } catch (err: any) {
    message.error('保存配置失败: ' + (err.message || '未知错误'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.admin-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 16px 96px;
}

.admin-page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32px;
}

.eyebrow {
  font-size: 13px;
  letter-spacing: 1px;
  color: var(--color-text-secondary);
  margin: 0 0 4px;
}

h1 {
  font-size: 28px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text);
}

.empty-state {
  text-align: center;
  padding: 64px 0;
  color: var(--color-text-secondary);
}

.config-section {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}

.config-section h2 {
  font-size: 18px;
  margin: 0 0 16px;
  font-weight: 600;
}

.section-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 16px;
}

.labels-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.label-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label-key {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-family: monospace;
}

.table-container {
  overflow-x: auto;
}

.quota-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.quota-table th, .quota-table td {
  padding: 12px 8px;
  border-bottom: 1px solid var(--color-border);
}

.quota-table th {
  font-weight: 500;
  color: var(--color-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.level-col {
  font-weight: 600;
  color: var(--color-primary);
}
</style>
