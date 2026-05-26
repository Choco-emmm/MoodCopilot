/// <reference types="vite/client" />

interface Window {
  $message?: any
  $notification?: any
}

declare module '@wangeditor/editor-for-vue' {
  import type { IDomEditor, IToolbarConfig, IEditorConfig } from '@wangeditor/editor'
  import type { DefineComponent } from 'vue'

  export const Editor: DefineComponent<{
    mode?: string
    defaultContent?: any[]
    defaultHtml?: string
    defaultConfig?: Partial<IEditorConfig>
    modelValue?: string
  }, {}, any>

  export const Toolbar: DefineComponent<{
    editor: IDomEditor | null
    mode?: string
    defaultConfig?: Partial<IToolbarConfig>
  }, {}, any>
}
