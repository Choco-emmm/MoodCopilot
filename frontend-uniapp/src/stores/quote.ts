import { customRef } from 'vue';
import { extractPlainText } from '../utils/markdown';

const currentOwner = (): string | null => {
  const id = uni.getStorageSync('loginUserId');
  return uni.getStorageSync('token') && id ? String(id) : null;
};
let owner = String(uni.getStorageSync('activeQuoteOwner') || '');
let value: string | null = owner && owner === currentOwner()
  ? uni.getStorageSync('activeQuote') || null : null;
if (!value) {
  uni.removeStorageSync('activeQuote');
  uni.removeStorageSync('activeQuoteOwner');
}
// Storage identity is not a Vue dependency: check every read, including send.
export const activeQuote = customRef<string | null>((track, trigger) => ({
  get() { track(); return currentOwner() === owner ? value : null; },
  set(text) {
    owner = currentOwner() || '';
    value = owner && text ? text : null;
    if (value) {
      uni.setStorageSync('activeQuote', value);
      uni.setStorageSync('activeQuoteOwner', owner);
    } else {
      uni.removeStorageSync('activeQuote');
      uni.removeStorageSync('activeQuoteOwner');
    }
    trigger();
  },
}));
export const setQuote = (text: string) => { activeQuote.value = text ? extractPlainText(text) || null : null; };
export const clearQuote = () => { activeQuote.value = null; };
