<template>
  <main class="app-shell">
    <AppHeader />

    <DiaryComposer
      v-if="!loadingEdit"
      :edit-id="editId"
      :initial-content="editContent"
      :initial-visibility="editVisibility"
      :initial-music-meta="editMusicMeta"
      :initial-images="editImages"
      :initial-lyric="editLyric"
      :initial-song-url="editSongUrl"
    />
    <div v-else class="panel" style="text-align:center;padding:40px;">加载中...</div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import DiaryComposer from '../components/DiaryComposer.vue'
import { diaryApi } from '../api'
import type { MusicMeta } from '../stores/diary'

const route = useRoute()
const loadingEdit = ref(!!route.query.edit)
const editId = ref<number | undefined>(undefined)
const editContent = ref('')
const editVisibility = ref<'PRIVATE' | 'PUBLIC'>('PRIVATE')
const editMusicMeta = ref<MusicMeta | null>(null)
const editImages = ref<string[]>([])
const editLyric = ref('')
const editSongUrl = ref('')

onMounted(async () => {
  const idParam = route.query.edit
  if (idParam) {
    const id = Number(idParam)
    if (Number.isFinite(id) && id > 0) {
      loadingEdit.value = true
      try {
        const res = await diaryApi.get(id)
        const d = res.data.data
        editId.value = d.id
        editContent.value = d.content || ''
        editVisibility.value = d.visibility === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE'
        editMusicMeta.value = d.musicMeta || null
        editImages.value = d.images || []
        editLyric.value = d.musicMeta?.userLyric || ''
        editSongUrl.value = d.musicMeta?.songUrl || ''
      } finally {
        loadingEdit.value = false
      }
    }
  }
})
</script>
