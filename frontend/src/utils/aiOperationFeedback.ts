export interface AiOperationFeedback {
  success(message: string): void
  error(message: string): void
  destroy(): void
}

export function beginAiOperation(label: string): AiOperationFeedback {
  const loading = window.$message?.loading(`MoodCopilot 正在${label}...`, { duration: 0 })
  let closed = false
  const destroy = () => {
    if (closed) return
    closed = true
    loading?.destroy()
  }
  return {
    success(message) { destroy(); window.$message?.success(message, { duration: 5000 }) },
    error(message) { destroy(); window.$message?.error(message, { duration: 5000 }) },
    destroy,
  }
}
