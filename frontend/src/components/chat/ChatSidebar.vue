<template>
  <aside class="chat-sidebar">
    <div class="sidebar-head">
      <span class="sidebar-title">对话</span>
      <button class="sidebar-new" :disabled="creatingConversation" @click="$emit('create')">+ 新建</button>
    </div>
    <div class="conv-list">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        :class="['conv-item', { active: conv.id === activeConvId }]"
        @click="$emit('select', conv.id)"
      >
        <span class="conv-title">{{ conv.title }}</span>
        <button
          class="conv-delete"
          @click.stop="$emit('delete', conv.id)"
        >&times;</button>
      </div>
      <div v-if="conversations.length === 0" class="conv-empty">暂无对话</div>
    </div>
  </aside>
</template>

<script setup lang="ts">
export interface Conversation {
  id: number
  title: string
}

defineProps<{
  conversations: Conversation[]
  activeConvId: number | null
  creatingConversation: boolean
}>()

defineEmits<{
  (e: 'create'): void
  (e: 'select', id: number): void
  (e: 'delete', id: number): void
}>()
</script>

<style scoped>
.chat-sidebar {
  border: none;
  border-radius: 14px;
  background: color-mi