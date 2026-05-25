export interface ThemeOption {
  value: string;
  label: string;
  primary: string;
  accent: string;
  bg: string;
  surface: string;
}

export const themeOptions: ThemeOption[] = [
  { value: 'green',          label: '绿意轻盈', primary: '#3b6b52', accent: '#a94b45', bg: '#f8f6f1', surface: '#fffdf8' },
  { value: 'sakura-blush',   label: '樱落桃酥', primary: '#c42878', accent: '#e8a04e', bg: '#fff6fb', surface: '#ffffff' },
  { value: 'berry-whisper',  label: '莓语轻紫', primary: '#9e3a78', accent: '#c0804e', bg: '#faf4f8', surface: '#ffffff' },
  { value: 'dream-rainbow',  label: '梦境彩虹', primary: '#5a80d8', accent: '#c25e98', bg: '#f6f8fd', surface: '#ffffff' },
  { value: 'cloud-blue',     label: '云屿蓝天', primary: '#007a85', accent: '#c0804e', bg: '#f0fafe', surface: '#ffffff' },
  { value: 'cloud-song',     label: '云端清歌', primary: '#2b8692', accent: '#e4be52', bg: '#f4fbfa', surface: '#ffffff' },
  { value: 'dusk-apple',     label: '晚风苹末', primary: '#4e736d', accent: '#c28b7e', bg: '#f4f6f5', surface: '#ffffff' },
  { value: 'jujube-roll',    label: '枣泥豆卷', primary: '#944a42', accent: '#d2ad7e', bg: '#fdfbf6', surface: '#ffffff' },
  { value: 'forest-matcha',  label: '森屿抹茶', primary: '#58802d', accent: '#a45a52', bg: '#f6f9f4', surface: '#ffffff' },
  { value: 'frost-blue',     label: '薄霜暮蓝', primary: '#426682', accent: '#7c8a9e', bg: '#f2f6f9', surface: '#ffffff' },
  { value: 'warm-wood',      label: '拾木暖尘', primary: '#664B3A', accent: '#8a7a4a', bg: '#f9f5f0', surface: '#ffffff' },
  { value: 'moss-cherry',    label: '苔野春樱', primary: '#4D613C', accent: '#c47a88', bg: '#faf6f7', surface: '#ffffff' },
  { value: 'salt-lemon',     label: '海盐柠泡', primary: '#388b98', accent: '#eab308', bg: '#e2f4f6', surface: '#ffffff' },
  { value: 'morning-dew',    label: '露露晨屿', primary: '#b8585e', accent: '#6db5be', bg: '#fff5f6', surface: '#ffffff' },
  { value: 'lemon-grape',    label: '柠香青提', primary: '#688a1e', accent: '#f0d83a', bg: '#fcfdf4', surface: '#ffffff' },
  { value: 'matcha-mist',    label: '抹茶微岚', primary: '#587585', accent: '#b5a86e', bg: '#f8fbf5', surface: '#ffffff' },
  { value: 'clear-summer',   label: '晴川浅夏', primary: '#2a865b', accent: '#f29c38', bg: '#f0faf5', surface: '#ffffff' },
  { value: 'black-rice',     label: '黑米潮糕', primary: '#ffb400', accent: '#29c4e0', bg: '#2b2b29', surface: '#474744' },
];
