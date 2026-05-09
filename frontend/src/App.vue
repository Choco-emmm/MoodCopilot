<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <router-view v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" />
      </Transition>
    </router-view>
  </n-config-provider>
</template>

<script setup lang="ts">
import type { GlobalThemeOverrides } from 'naive-ui'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#4a7c62',
    primaryColorHover: '#3d6a52',
    primaryColorPressed: '#345b46',
    primaryColorSuppl: '#4a7c62',
    infoColor: '#b5343a',
    infoColorHover: '#9e2d33',
    infoColorPressed: '#87262c',
    infoColorSuppl: '#b5343a',
    errorColor: '#b5343a',
    errorColorHover: '#9e2d33',
    errorColorPressed: '#87262c',
    successColor: '#4a7c62',
    successColorHover: '#3d6a52',
    successColorPressed: '#345b46',
    warningColor: '#c8843c',
    warningColorHover: '#b87635',
    warningColorPressed: '#a0682d',
  },
  Button: {
    colorPrimary: '#4a7c62',
    colorPrimaryHover: '#3d6a52',
    colorPrimaryPressed: '#345b46',
    textColorPrimary: '#fdfbf7',
    borderRadiusSmall: '4px',
    borderRadiusMedium: '6px',
    borderRadiusLarge: '10px',
  },
  Input: {
    borderFocus: '#4a7c62',
    borderHover: '#d4cdbc',
    borderRadius: '6px',
  },
  Tag: {
    borderRadius: '4px',
  },
  Badge: {
    color: '#b5343a',
  },
}
</script>
