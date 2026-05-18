<template>
  <main class="app-shell">
    <AppHeader />

    <section class="panel profile-head">
      <n-button v-if="isOwner" quaternary circle style="position: absolute; right: 16px; top: 16px; font-size: 20px;" @click="router.push('/settings')">⚙️</n-button>
      <div class="profile-hero">
        <div class="profile-avatar-wrap">
          <div v-if="profileLoading" class="profile-avatar">
            <n-spin size="small" />
          </div>
          <img v-else-if="profileAvatar" :src="profileAvatar" class="profile-avatar profile-avatar-img" decoding="async" />
          <span v-else class="profile-avatar">{{ profileInitial }}</span>
        </div>
        <div class="profile-main">
          <h2 class="profile-title">{{ profileLoading ? '加载中...' : (isOwner ? '我的日记' : profileName) }}</h2>
          <p class="profile-signature">{{ profileSignature || (isOwner ? '还没有写签名，去个人中心补一句吧。' : '这个人很低调，还没留下签名。') }}</p>
        </div>
      </div>
    </section>

    <section class="panel profile-list-panel">
      <div class="profile-list-head">
        <h3>日记列表</h3>
        <n-button quaternary size="small" :loading="loading" @click="reload">刷新</n-button>
      </div>

      <div v-if="diaries.length" class="feed">
        <DiaryFeedItem
          v-for="diary in diaries"
          :key="diary.id"
          :diary="diary"
          :enable-comments="false"
          :compact="true"
          :preview-limit="120"
          :show-expand-toggle="false"
          @resonate="(d: Diary) => store.resonate(d.id)"
          @open-detail="(d: Diary) => router.push(`/diary/${d.id}`)"
        />

        <div v-if="hasMore" class="profile-load-more">
          <n-button secondary block :loading="loadingMore" @click="loadMore">加载更多</n-button>
        </div>
      </div>

      <div v-else-if="!loading" class="profile-empty-wrap">
        <n-empty :description="isOwner ? '你还没有写日记' : '暂无公开日记'" />
        <p class="profile-empty-tip">{{ isOwner ? '从一条简单记录开始，持续比完美更重要。' : '晚点再来看看，或先去广场看看大家的分享。' }}</p>
      </div>
      <n-spin v-else size="small" />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NEmpty, NSpin } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import DiaryFeedItem from '../components/DiaryFeedItem.vue'
import { authApi, diaryApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { useDiaryStore, type Diary } from '../stores/diary'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useDiaryStore()

const loading = ref(false)
const loadingMore = ref(false)
const page = ref(1)
const total = ref(0)
const diaries = ref<Diary[]>([])
const profileName = ref('')
const profileAvatar = ref<string | null>(null)
const profileSignature = ref('')
const profileLoading = ref(true)

const profileUserId = computed(() => Number(route.params.userId))
const isOwner = computed(() => auth.userId != null && auth.userId === profileUserId.value)
const hasMore = computed(() => diaries.value.length < total.value)
const profileInitial = computed(() => (profileName.value || '用').charAt(0))

onMounted(() => {
  void reload()
})

watch(() => route.params.userId, () => {
  void reload()
})

async function reload() {
  if (!Number.isFinite(profileUserId.value)) {
    diaries.value = []
    total.value = 0
    return
  }

  loading.value = true
  profileLoading.value = true
  page.value = 1
  try {
    const [profileRes, diaryRes] = await Promise.all([
      authApi.profile(profileUserId.value),
      isOwner.value ? diaryApi.mine(1, 20) : diaryApi.byUser(profileUserId.value, 1, 20),
    ])
    const profile = profileRes.data.data
    profileName.value = profile?.displayName || (isOwner.value ? auth.displayName || '我' : '用户')
    profileAvatar.value = profile?.avatar || (isOwner.value ? auth.avatar : null)
    profileSignature.value = profile?.signature || ''

    const data = diaryRes.data.data
    const items = (data.items ?? []).map(store.normalize)
    diaries.value = items
    total.value = data.total ?? items.length
  } finally {
    profileLoading.value = false
    loading.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const res = isOwner.value
      ? await diaryApi.mine(nextPage, 20)
      : await diaryApi.byUser(profileUserId.value, nextPage, 20)
    const data = res.data.data
    const items = (data.items ?? []).map(store.normalize)
    const existing = new Set(diaries.value.map(d => d.id))
    diaries.value.push(...items.filter((item: Diary) => !existing.has(item.id)))
    total.value = data.total ?? diaries.value.length
    page.value = nextPage
  } finally {
    loadingMore.value = false
  }
}
</script>

<style scoped>
.profile-head {
  position: relative;
  margin-bottom: 12px;
  padding: 16px;
}

.profile-hero {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.profile-avatar-wrap {
  flex-shrink: 0;
}

.profile-avatar {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--color-primary), #5A9470);
  border: 1px solid rgba(59, 107, 79, 0.35);
}

.profile-avatar-img {
  object-fit: cover;
}

.profile-main {
  min-width: 0;
}

.profile-title {
  margin: 0;
  font-family: var(--font-body);
  color: var(--color-text);
  font-size: clamp(24px, 4.8vw, 38px);
  line-height: 1.14;
  letter-spacing: -0.01em;
}

.profile-signature {
  margin: 10px 0 0;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.55;
}

.profile-list-panel {
  min-height: 180px;
  padding: 16px;
}

.profile-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.profile-list-head h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 18px;
  line-height: 1.2;
}

.profile-empty-wrap {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 8px 0 2px;
}

.profile-empty-tip {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.profile-load-more {
  margin-top: 10px;
}

@media (max-width: 780px) {
  .profile-head,
  .profile-list-panel {
    padding: 14px;
  }

  .profile-avatar {
    width: 52px;
    height: 52px;
    font-size: 20px;
  }

  .profile-signature {
    font-size: 13px;
  }

  .profile-list-head h3 {
    font-size: 17px;
  }
}
</style>
