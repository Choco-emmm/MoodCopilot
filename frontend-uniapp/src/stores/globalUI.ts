import { ref } from 'vue';
import { currentTheme } from './theme';

export interface PopupItem {
  id: number;
  title: string;
  message?: string;
  icon: string;
  type: 'MEMORY' | 'GRAPH';
}

export interface ModalItem {
  title: string;
  message: string;
  diaryId?: number;
}

export const popups = ref<PopupItem[]>([]);
export const currentModal = ref<ModalItem | null>(null);

let popupIdCounter = 0;

export const showPopup = (title: string, message: string, type: 'MEMORY' | 'GRAPH', duration = 4000) => {
  const id = ++popupIdCounter;
  const primary = encodeURIComponent(currentTheme.value.primary);
  const svgMemory = `data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='${primary}'%3E%3Cpath d='M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z'/%3E%3C/svg%3E`;
  const svgGraph = `data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='${primary}'%3E%3Cpath d='M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3 0 .14.03.28.05.41L9.12 10.7c-.55-.45-1.25-.7-2.12-.7-1.66 0-3 1.34-3 3s1.34 3 3 3c.87 0 1.57-.25 2.12-.7l3.93 2.29c-.02.13-.05.27-.05.41 0 1.66 1.34 3 3 3s3-1.34 3-3-1.34-3-3-3c-.87 0-1.57.25-2.12.7l-3.93-2.29c.02-.13.05-.27.05-.41 0-.14-.03-.28-.05-.41l3.93-2.29c.55.45 1.25.7 2.12.7z'/%3E%3C/svg%3E`;

  popups.value.push({ id, title, message, type, icon: type === 'MEMORY' ? svgMemory : svgGraph });
  
  if (duration > 0) {
    setTimeout(() => {
      removePopup(id);
    }, duration);
  }
};

export const removePopup = (id: number) => {
  popups.value = popups.value.filter(p => p.id !== id);
};

export const showModal = (title: string, message: string, diaryId?: number) => {
  currentModal.value = { title, message, diaryId };
};

export const closeModal = () => {
  currentModal.value = null;
};
