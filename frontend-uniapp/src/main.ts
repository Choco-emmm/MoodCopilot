import { createSSRApp } from "vue";
import App from "./App.vue";
import { themeStyle, syncNavigationBarColor } from "@/stores/theme";
import { onShow } from "@dcloudio/uni-app";

export function createApp() {
  const app = createSSRApp(App);
  
  // Global Mixin for Theme
  app.mixin({
    computed: {
      globalThemeStyle() {
        return themeStyle.value;
      }
    },
    onShow() {
      // sync native navigation bar on every page show
      syncNavigationBarColor();
    }
  });

  return {
    app,
  };
}
