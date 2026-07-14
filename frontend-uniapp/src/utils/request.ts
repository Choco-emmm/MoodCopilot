export interface Result<T = any> {
  code: number;
  message: string;
  data: T;
}

export const BASE_URL = 'http://localhost:18080';

export const getFullUrl = (url: string | undefined | null): string => {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) return url;
  if (url.startsWith('/api') || url.startsWith('/uploads')) return BASE_URL + url;
  return url;
};

export const request = <T = any>(
  url: string,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
  data?: any,
  header?: any
): Promise<Result<T>> => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token');
    const customHeader = {
      ...header,
      'Content-Type': 'application/json',
    };
    if (token) {
      customHeader['Authorization'] = `Bearer ${token}`;
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
        } else if (res.statusCode === 401) {
          uni.removeStorageSync('token');
          uni.showToast({
            title: '请先登录',
            icon: 'none',
          });
          uni.$emit('unauthorized');
          reject(new Error('Unauthorized'));
        } else {
          uni.showToast({
            title: res.data?.message || '请求失败',
            icon: 'none',
          });
          reject(new Error(res.data?.message || '请求失败'));
        }
      },
      fail: (err) => {
        uni.showToast({
          title: '网络错误，请稍后重试',
          icon: 'none',
        });
        reject(err);
      },
    });
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
        } else if (res.statusCode === 401) {
          uni.removeStorageSync('token');
          uni.showToast({ title: '请先登录', icon: 'none' });
          // Option: Redirect to profile here if needed, or emit an event
          uni.$emit('unauthorized');
          reject(new Error('Unauthorized'));
        } else {
          uni.showToast({ title: '上传失败', icon: 'none' });
          reject(new Error('上传失败'));
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' });
        reject(err);
      },
    });
  });
};
