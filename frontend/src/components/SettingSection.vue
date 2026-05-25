<template>
  <section class="settings-section" :class="extraClass">
    <div class="section-head">
      <label class="settings-label">{{ title }}</label>
      <span class="section-tag">{{ tag }}</span>
    </div>
    <slot></slot>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  tag: string
  extraClass?: string
}>()
</script>

<style scoped>
.settings-section {
  margin-top: 12px;
  padding: 16px;
  border-radius: 16px;
  background: var(--color-surface);
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
  box-shadow: 0 2px 10px color-mix(in oklab, var(--color-text) 3%, transparent);
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.settings-label {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
}

.section-tag {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 12%, transparent);
  border: 1px solid color-mix(in oklab, var(--color-primary) 18%, transparent 82%);
  border-radius: 999px;
  padding: 2px 9px;
}

/* Inherited or specific classes can be passed via extraClass */
.settings-section.danger-zone {
  /* specific styling if any */
}
.settings-section.support-donate-section {
  text-align: center;
}
.settings-section.support-donate-section .section-head {
  justify-content: center;
}
</style>
