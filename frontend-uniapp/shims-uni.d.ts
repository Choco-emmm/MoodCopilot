/// <reference types='@dcloudio/types' />
import 'vue'

declare module 'vue' {
  interface ComponentCustomProperties {
    globalThemeStyle: string;
  }
}

declare module '@vue/runtime-core' {
  type Hooks = App.AppInstance & Page.PageInstance;

  interface ComponentCustomOptions extends Hooks {

  }
}
