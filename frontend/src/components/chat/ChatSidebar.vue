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
      >
        <button class="conv-select" type="button" @click="$emit('select', conv.id)">
          <span class="conv-title">{{ displayConversationTitle(conv.title, conv.id) }}</span>
        </button>
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
import { displayConversationTitle } from '../../utils/chatTitle'

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
  background: color-mix(in oklab, var(--color-surface) 80%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  overflow: hidden;
  box-shadow:
    0 1px 2px rgba(32,32,29,0.03),
    0 4px 12px color-mix(in oklab, var(--color-primary) 4%, transparent);
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid color-mix(in oklab, var(--color-primary) 10%, transparent);
}

.sidebar-title {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.sidebar-new {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--color-primary);
  background: none;
  border: none;
  cursor: pointer;
  padding: 3px 10px;
  border-radius: 6px;
  transition: background 0.15s;
  font-family: inherit;
}

.sidebar-new:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
}

.sidebar-new:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.conv-list {
  display: grid;
}

.conv-item {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  border-bottom: 1px solid color-mix(in oklab, var(--color-primary) 5%, transparent);
  transition: background 0.15s;
}

.conv-item:hover {
  background: color-mix(in oklab, var(--color-primary) 4%, transparent);
}

.conv-select {
  min-width: 0;
  padding: 12px 0 12px 16px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.conv-select:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.conv-item.active {
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
  box-shadow: inset 2px 0 0 var(--color-primary);
}

.conv-title {
  display: block;
  font-size: 0.85rem;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-delete {
  opacity: 0;
  background: none;
  border: none;
  color: var(--color-accent);
  font-size: 16px;
  cursor: pointer;
  padding: 0 4px;
  transition: opacity 0.15s;
  font-family: inherit;
}

.conv-item:hover .conv-delete {
  opacity: 1;
}

.conv-empty {
  padding: 32px 12px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-style: italic;
}
</style>
