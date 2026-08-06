import { post, BASE_URL } from './request';

const WS_BASE_URL = BASE_URL.replace(/^http/i, 'ws') + '/ws/notifications';

let socketTask: any = null;
let isConnected = false;
let reconnectTimer: any = null;
let isConnecting = false;
let disconnectRequested = false;

export const connectWebSocket = async () => {
  const token = uni.getStorageSync('token');
  if (isConnected || !token || socketTask || isConnecting) return;
  disconnectRequested = false;
  isConnecting = true;

  try {
    const res = await post('/api/notifications/ws-ticket');
    if (res.code === 200 && res.data && res.data.ticket) {
      const ticket = res.data.ticket;
      
      socketTask = uni.connectSocket({
        url: `${WS_BASE_URL}?ticket=${ticket}`,
        success: () => {
          console.log('WebSocket connection initialized');
        },
        fail: () => {
          console.error('WebSocket connection failed');
          socketTask = null;
          scheduleReconnect();
        }
      });

      socketTask.onOpen(() => {
        console.log('WebSocket connected');
        isConnected = true;
        if (reconnectTimer) {
          clearTimeout(reconnectTimer);
          reconnectTimer = null;
        }
        startHeartbeat();
      });

      socketTask.onMessage((res: any) => {
        try {
          const payload = JSON.parse(res.data);
          handleWebSocketMessage(payload);
        } catch (e) {
          console.error('Failed to parse WS message', e);
        }
      });

      socketTask.onClose(() => {
        console.log('WebSocket closed');
        isConnected = false;
        socketTask = null;
        stopHeartbeat();
        scheduleReconnect();
      });
      
      socketTask.onError((err: any) => {
        console.error('WebSocket error', err);
        isConnected = false;
        socketTask = null;
        stopHeartbeat();
        scheduleReconnect();
      });
    }
  } catch (e) {
    console.error('Failed to get ws ticket', e);
    scheduleReconnect();
  } finally {
    isConnecting = false;
  }
};

import { showPopup, showModal } from './../stores/globalUI';

const handleWebSocketMessage = (payload: any) => {
  const type = payload.type;
  if (type === 'MEMORY_UPDATED') {
    showPopup('已生成新的专属记忆', payload.data?.message || '', 'MEMORY', 4000);
    uni.$emit('refreshMemory');
  } else if (type === 'GRAPH_UPDATED') {
    showPopup('关系图谱已扩展', payload.data?.message || '', 'GRAPH', 4000);
    uni.$emit('refreshGraph');
  } else if (type === 'AI_ANALYSIS_COMPLETE') {
    showModal('日记分析已完成', payload.data?.message || '你的日记有了新的 AI 解读，快来看看吧！', payload.data?.diaryId);
    uni.$emit('refreshFeed');
    uni.$emit('refreshAnalysis');
  } else if (['COMMENT', 'RESONANCE', 'FOLLOW'].includes(type)) {
    // 小程序是私密日记工具，不向用户暴露网页端的社交事件。
    return;
  } else {
    // Other notifications
    uni.$emit('refreshNotifications');
  }
};

let heartbeatTimer: any = null;
const startHeartbeat = () => {
  heartbeatTimer = setInterval(() => {
    if (socketTask && isConnected) {
      socketTask.send({
        data: 'ping',
        success: () => console.log('Heartbeat sent'),
        fail: () => console.error('Heartbeat failed')
      });
    }
  }, 30000);
};

const stopHeartbeat = () => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
};

const scheduleReconnect = () => {
  if (disconnectRequested || reconnectTimer || !uni.getStorageSync('token')) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectWebSocket();
  }, 5000);
};

export const disconnectWebSocket = () => {
  disconnectRequested = true;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  stopHeartbeat();
  isConnected = false;
  const currentTask = socketTask;
  socketTask = null;
  currentTask?.close();
};
