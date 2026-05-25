/**
 * Type-safe wrapper around NaiveUI useMessage().
 * Call inside <script setup> — relies on the <n-message-provider> ancestor.
 */
import { useMessage, useNotification } from 'naive-ui'

export function useAppMessage() {
  return useMessage()
}

export function useAppNotification() {
  return useNotification()
}
