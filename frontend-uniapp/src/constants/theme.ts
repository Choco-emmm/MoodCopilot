export interface ThemeOption {
  value: string;
  label: string;
  primary: string;
  accent: string;
  bg: string;
  surface: string;
  dark?: boolean;
  category?: string;
}

export const themeOptions: ThemeOption[] = [
  // 草木清欢 (Original Web Style)
  { value: 'green',          label: '经典复古', primary: '#4a7c62', accent: '#a33f3f', bg: '#f4f0ea', surface: '#ffffff', category: '🌱 草木清欢' },
  { value: 'forest-matcha',  label: '森屿抹茶', primary: '#58802d', accent: '#a45a52', bg: '#f6f9f4', surface: '#ffffff', category: '🌱 草木清欢' },
  { value: 'moss-cherry',    label: '苔野春樱', primary: '#4D613C', accent: '#c47a88', bg: '#faf6f7', surface: '#ffffff', category: '🌱 草木清欢' },
  { value: 'willow-breeze',  label: '柳岸春风', primary: '#699859', accent: '#c2a048', bg: '#f4f9f2', surface: '#ffffff', category: '🌱 草木清欢' },
  { value: 'lemon-grape',    label: '柠香青提', primary: '#688a1e', accent: '#f0d83a', bg: '#fcfdf4', surface: '#ffffff', category: '🌱 草木清欢' },
  { value: 'clear-summer',   label: '晴川浅夏', primary: '#2a865b', accent: '#f29c38', bg: '#f0faf5', surface: '#ffffff', category: '🌱 草木清欢' },
  { value: 'bamboo-whisper', label: '竹影清风', primary: '#4a7c59', accent: '#d4a373', bg: '#f4f7f4', surface: '#ffffff', category: '🌱 草木清欢' },
  { value: 'salt-lemon',     label: '海盐柠泡', primary: '#388b98', accent: '#eab308', bg: '#e2f4f6', surface: '#ffffff', category: '🌱 草木清欢' },
  
  // 治愈午后
  { value: 'jujube-roll',    label: '枣泥豆卷', primary: '#944a42', accent: '#d2ad7e', bg: '#fdfbf6', surface: '#ffffff', category: '☕️ 治愈午后' },
  { value: 'warm-wood',      label: '拾木暖尘', primary: '#664B3A', accent: '#8a7a4a', bg: '#f9f5f0', surface: '#ffffff', category: '☕️ 治愈午后' },
  { value: 'apricot-breeze', label: '杏雨微风', primary: '#d96a47', accent: '#e3a052', bg: '#fcf6f2', surface: '#ffffff', category: '☕️ 治愈午后' },
  { value: 'oat-milk',       label: '燕麦奶盖', primary: '#a88863', accent: '#8da674', bg: '#faf8f5', surface: '#ffffff', category: '☕️ 治愈午后' },
  { value: 'chestnut-cocoa', label: '栗香可可', primary: '#8b5a44', accent: '#c47c5d', bg: '#fcf8f5', surface: '#ffffff', category: '☕️ 治愈午后' },

  // 静谧诗意
  { value: 'cloud-blue',     label: '云屿蓝天', primary: '#007a85', accent: '#c0804e', bg: '#f0fafe', surface: '#ffffff', category: '🌧️ 静谧诗意' },
  { value: 'cloud-song',     label: '云端清歌', primary: '#2b8692', accent: '#e4be52', bg: '#f4fbfa', surface: '#ffffff', category: '🌧️ 静谧诗意' },
  { value: 'frost-blue',     label: '薄霜暮蓝', primary: '#426682', accent: '#7c8a9e', bg: '#f2f6f9', surface: '#ffffff', category: '🌧️ 静谧诗意' },
  { value: 'lavender-dawn',  label: '晨雾紫藤', primary: '#7e68a8', accent: '#6896a8', bg: '#f6f5fc', surface: '#ffffff', category: '🌧️ 静谧诗意' },
  { value: 'matcha-mist',    label: '抹茶微岚', primary: '#587585', accent: '#b5a86e', bg: '#f8fbf5', surface: '#ffffff', category: '🌧️ 静谧诗意' },
  { value: 'dusk-apple',     label: '晚风苹末', primary: '#4e736d', accent: '#c28b7e', bg: '#f4f6f5', surface: '#ffffff', category: '🌧️ 静谧诗意' },
  { value: 'ink-wash-mist',  label: '水墨微岚', primary: '#546a76', accent: '#88a0a8', bg: '#f5f6f8', surface: '#ffffff', category: '🌧️ 静谧诗意' },

  // 梦境童话
  { value: 'berry-whisper',  label: '莓语轻紫', primary: '#9e3a78', accent: '#c0804e', bg: '#faf4f8', surface: '#ffffff', category: '✨ 梦境童话' },
  { value: 'dream-rainbow',  label: '梦境彩虹', primary: '#5a80d8', accent: '#c25e98', bg: '#f6f8fd', surface: '#ffffff', category: '✨ 梦境童话' },
  { value: 'morning-dew',    label: '露露晨屿', primary: '#b8585e', accent: '#6db5be', bg: '#fff5f6', surface: '#ffffff', category: '✨ 梦境童话' },
  { value: 'starlight-coral',label: '星芒珊瑚', primary: '#d97381', accent: '#e8b277', bg: '#fdf6f7', surface: '#ffffff', category: '✨ 梦境童话' },
  { value: 'sakura-blush',   label: '樱落桃酥', primary: '#c42878', accent: '#e8a04e', bg: '#fff6fb', surface: '#ffffff', category: '✨ 梦境童话' },

  // 夜间主题 (无 category)
  { value: 'minimal-dark',   label: '极简暗夜', primary: '#8a8e96', accent: '#6b8aa8', bg: '#0e0e0e', surface: '#1a1a1a', dark: true },
  { value: 'black-rice',     label: '黑米潮糕', primary: '#ffb400', accent: '#29c4e0', bg: '#2b2b29', surface: '#474744', dark: true },
  { value: 'abyss-blue',     label: '深渊海蓝', primary: '#4a8b9e', accent: '#c49a3e', bg: '#0f172a', surface: '#1e293b', dark: true },
  { value: 'midnight-pine',  label: '暮夜松林', primary: '#5b8a72', accent: '#c27c6b', bg: '#121a16', surface: '#1e2b24', dark: true },
  { value: 'mocha-night',    label: '浓醇摩卡', primary: '#d4a373', accent: '#b86054', bg: '#1a1614', surface: '#29231f', dark: true },
  { value: 'cyber-purple',   label: '紫曜星河', primary: '#8592bd', accent: '#c4a1b0', bg: '#212638', surface: '#2b334d', dark: true },
  { value: 'firefly-forest', label: '萤火之森', primary: '#6bb38a', accent: '#dce87d', bg: '#0d1411', surface: '#16241d', dark: true },
];
