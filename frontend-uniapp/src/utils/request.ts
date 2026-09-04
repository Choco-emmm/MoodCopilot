export interface Result<T = any> {
  code: number;
  message: string;
  data: T;
}

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
const isWeChatMiniProgram = typeof globalThis !== 'undefined' && 'wx' in globalThis;
const defaultBaseUrl = import.meta.env.DEV && !isWeChatMiniProgram
  ? 'http://localhost:18080'
  : 'https://moodcopilot.top';

export const BASE_URL = (configuredBaseUrl || defaultBaseUrl).replace(/\/+$/, '');

export const getFullUrl = (url: string | undefined | null): string => {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) return url;
  if (url.startsWith('/api') || url.startsWith('/uploads')) return BASE_URL + url;
  return url;
};

// 队列，用于存储在刷新 token 期间过来的请求
let isRefreshing = false;
let requestsQueue: any[] = [];

// 执行队列中的请求
const processQueue = (error: Error | null, token?: string) => {
  requestsQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  requestsQueue = [];
};

// 微信静默登录
const silentLogin = (): Promise<string> => {
  return new Promise((resolve, reject) => {
    if (!isWeChatMiniProgram) {
      reject(new Error('Not in WeChat'));
      return;
    }
    uni.login({
      provider: 'weixin',
      success: (loginRes) => {
        if (!loginRes.code) return reject(new Error('No login code'));
        uni.request({
          url: BASE_URL + '/api/auth/wx-login',
          method: 'POST',
          data: { code: loginRes.code },
          header: { 'Content-Type': 'application/json' },
          success: (res: any) => {
            if (res.statusCode === 200 && res.data?.code === 200 && res.data?.data?.token) {
              const newToken = res.data.data.token;
              uni.setStorageSync('token', newToken);
              resolve(newToken);
            } else {
              reject(new Error('Silent login failed'));
            }
          },
          fail: (err) => reject(err)
        });
      },
      fail: (err) => reject(err)
    });
  });
};

// 统一的错误与未授权处理逻辑
const extractErrorMessage = (value: unknown): string => {
  if (typeof value === 'string') return value.trim();
  if (Array.isArray(value)) {
    return value
      .map(item => extractErrorMessage(item))
      .filter(Boolean)
      .join('；');
  }
  if (value && typeof value === 'object') {
    const payload = value as Record<string, unknown>;
    for (const key of ['message', 'error', 'detail', 'msg']) {
      const message = extractErrorMessage(payload[key]);
      if (message) return message;
    }
  }
  return '';
};

const handleResponseError = (statusCode: number, dataMessage?: unknown, defaultMsg: string = '请求失败') => {
  if (statusCode === 401) {
    uni.removeStorageSync('token');
    uni.showToast({ title: '请先登录', icon: 'none' });
    uni.$emit('unauthorized');
    const error = new Error('Unauthorized') as Error & { statusCode?: number };
    error.statusCode = statusCode;
    return error;
  } else {
    const serverMessage = extractErrorMessage(dataMessage);
    const msg = [500, 502, 503, 504].includes(statusCode)
      ? '服务暂时不可用，请稍后再试'
      : statusCode === 404
        ? '功能暂时不可用，请稍后再试'
        : serverMessage || (statusCode === 429 ? '请求额度已用完，请稍后再试' : defaultMsg);
    uni.showToast({ title: msg, icon: 'none' });
    const error = new Error(msg) as Error & { statusCode?: number };
    error.statusCode = statusCode;
    return error;
  }
};

export const request = <T = any>(
  url: string,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
  data?: any,
  header?: any
): Promise<Result<T>> => {
  return new Promise((resolve, reject) => {
    const performRequest = (currentToken: string) => {
      const customHeader: any = {
        ...header,
        'Content-Type': 'application/json',
      };
      if (currentToken) {
        customHeader['Authorization'] = `Bearer ${currentToken}`;
      }

      uni.request({
        url: BASE_URL + url,
        method,
        data,
        header: customHeader,
        success: (res: any) => {
          if (res.statusCode === 200) {
            const result = res.data as Result<T>;
            if (result.code === 0) result.code = 200;
            resolve(result);
          } else if (res.statusCode === 401 && isWeChatMiniProgram) {
            // 静默登录续期逻辑
            if (!isRefreshing) {
              isRefreshing = true;
              silentLogin().then(newToken => {
                isRefreshing = false;
                processQueue(null, newToken);
                performRequest(newToken); // 重试当前请求
              }).catch(err => {
                isRefreshing = false;
                processQueue(err);
                reject(handleResponseError(401, '登录已过期，请重新登录'));
              });
            } else {
              // 已经在刷新，加入队列等待
              requestsQueue.push({
                resolve: (newToken: string) => performRequest(newToken),
                reject: (err: any) => reject(err)
              });
            }
          } else {
            reject(handleResponseError(res.statusCode, res.data?.message));
          }
        },
        fail: (err) => {
          const error = new Error('网络错误，请稍后重试') as Error & { statusCode?: number };
          error.statusCode = 0;
          uni.showToast({ title: error.message, icon: 'none' });
          reject(error);
        },
      });
    };

    performRequest(uni.getStorageSync('token'));
  });
};

export const get = <T = any>(url: string, data?: any, header?: any) => {
  return request<T>(url, 'GET', data, header);
};

export const post = <T = any>(url: string, data?: any, header?: any) => {
  return request<T>(url, 'POST', data, header);
};

export const put = <T = any>(url: string, data?: any, header?: any) => {
  return request<T>(url, 'PUT', data, header);
};

export const del = <T = any>(url: string, data?: any, header?: any) => {
  return request<T>(url, 'DELETE', data, header);
};

export const upload = <T = any>(url: string, filePath: string, name: string = 'file') => {
  return new Promise<Result<T>>((resolve, reject) => {
    const token = uni.getStorageSync('token');
    const header: any = {};
    if (token) {
      header['Authorization'] = `Bearer ${token}`;
    }

    uni.uploadFile({
      url: BASE_URL + url,
      filePath,
      name,
      header,
      success: (res: any) => {
        if (res.statusCode === 200) {
          try {
            const result = JSON.parse(res.data) as Result<T>;
            if (result.code === 0) result.code = 200;
            resolve(result);
          } catch (e) {
            reject(new Error('Parse error'));
          }
        } else {
          reject(handleResponseError(res.statusCode, undefined, '上传失败'));
        }
      },
      fail: (err) => {
        const error = new Error('网络错误，请稍后重试') as Error & { statusCode?: number };
        error.statusCode = 0;
        uni.showToast({ title: error.message, icon: 'none' });
        reject(error);
      },
    });
  });
};
