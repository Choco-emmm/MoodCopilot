import { ref } from 'vue';
import { extractPlainText } from '@/utils/markdown';

export const activeQuote = ref<string | null>(uni.getStorageSync('activeQuote') || null);

export const setQuote = (text: string) => {
  if (!text) {
    activeQuote.value = null;
    uni.removeStorageSync('activeQuote');
    return;
  }
  const cleaned = extractPlainText(text);
  activeQuote.value = cleaned;
  if (cleaned) {
    uni.setStorageSync('activeQuote', cleaned);
  } else {
    uni.removeStorageSync('activeQuote');
  }
};

export const clearQuote = () => {
  activeQuote.value = null;
  uni.removeStorageSync('activeQuote');
};
