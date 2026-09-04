import { post, BASE_URL } from './request';
import { showPopup, showModal } from './../stores/globalUI';

const WS_BASE_URL = BASE_URL.replace(/^http/i, 'ws') + '/ws/notifications';

let socketTask: any = null;
let isConnected = false;
let reconnectTimer: any = null;
let heartbeatTimer: any = null;
let heartbeatTimeoutTimer: any = null;
let isConnecting = false;
let disconnectRequested = false;

// Exponential backoff reconnect
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 10;
const INITIAL_RECONNECT_DELAY = 5000;
const MAX_RECONNECT_DELAY = 60000;

// Heartbeat pong tracking
let lastPongTime = 0;
const HEARTBEAT_INTERVAL = 30000;
const HEARTBEAT_TIMEOUT = 15000; // If no pong within 15s after ping, consider connection dead

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
        success: () => {},
        fail: () => {
          socketTask = null;
          scheduleReconnect();
        }
      });

      socketTask.onOpen(() => {
        isConnected = true;
        reconnectAttempts = 0; // Reset on successful connection
        if (reconnectTimer) {
          clearTimeout(reconnectTimer);
          reconnectTimer = null;
        }
        startHeartbeat();
      });

      socketTask.onMessage((res: any) => {
        try {
          const payload = JSON.parse(res.data);
          // Track pong responses
          if (payload.type === 'pong' || res.data === 'pong') {
            lastPongTime = Date.now();
            return;
          }
          handleWebSocketMessage(payload);
        } catch (e) {
          // Non-JSON message (e.g., plain "pong" string)
          if (res.data === 'pong') {
            lastPongTime = Date.now();
          }
        }
      });

      socketTask.onClose(() => {
        isConnected = false;
        socketTask = null;
        stopHeartbeat();
        scheduleReconnect();
      });

      socketTask.onError(() => {
        isConnected = false;
        socketTask = null;
        stopHeartbeat();
        scheduleReconnect();
      });
    }
  } catch (e) {
    scheduleReconnect();
  } finally {
    isConnecting = false;
  }
};

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
    uni.$emit('refreshNotifications');
  }
};

const startHeartbeat = () => {
  stopHeartbeat();
  lastPongTime = Date.now();
  heartbeatTimer = setInterval(() => {
    if (socketTask && isConnected) {
      socketTask.send({
        data: 'ping',
        success: () => {},
        fail: () => {
          // Heartbeat send failed — connection is likely dead
          forceReconnect();
        }
      });

      // Set a timeout to check if pong was received
      if (heartbeatTimeoutTimer) clearTimeout(heartbeatTimeoutTimer);
      heartbeatTimeoutTimer = setTimeout(() => {
        // No pong received within timeout — connection is half-open
        if (Date.now() - lastPongTime > HEARTBEAT_TIMEOUT) {
          forceReconnect();
        }
      }, HEARTBEAT_TIMEOUT);
    }
  }, HEARTBEAT_INTERVAL);
};

const stopHeartbeat = () => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
  if (heartbeatTimeoutTimer) {
    clearTimeout(heartbeatTimeoutTimer);
    heartbeatTimeoutTimer = null;
  }
};

/**
 * Force-close the current connection and trigger reconnection.
 * Used when heartbeat fails or pong timeout is detected.
 */
const forceReconnect = () => {
  if (!isConnected) return;
  stopHeartbeat();
  isConnected = false;
  const task = socketTask;
  socketTask = null;
  try {
    task?.close();
  } catch (e) {
    // Ignore close errors
  }
  scheduleReconnect();
};

const scheduleReconnect = () => {
  if (disconnectRequested || reconnectTimer || !uni.getStorageSync('token')) return;
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    // Stop reconnecting after max attempts; will retry on next app foreground (onShow)
    reconnectAttempts = 0;
    return;
  }

  // Exponential backoff: delay = min(initial * 2^attempts, max)
  const delay = Math.min(
    INITIAL_RECONNECT_DELAY * Math.pow(2, reconnectAttempts),
    MAX_RECONNECT_DELAY
  );
  reconnectAttempts++;

  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectWebSocket();
  }, delay);
};

export const disconnectWebSocket = () => {
  disconnectRequested = true;
  reconnectAttempts = 0;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  stopHeartbeat();
  isConnected = false;
  const currentTask = socketTask;
  socketTask = null;
  try {
    currentTask?.close();
  } catch (e) {
    // Ignore close errors
  }
};
