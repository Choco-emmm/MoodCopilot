/// <reference types="vite/client" />

declare global {
  interface Window {
    $message?: {
      success: (msg: string) => void
      error: (msg: string) => void
    }
  }
}

export {}
