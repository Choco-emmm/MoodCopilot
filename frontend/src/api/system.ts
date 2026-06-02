import { api } from './core'

export const imageApi = {
  upload: (file: File, compress = true) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post('/images/upload', fd, {
      params: { compress },
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  uploadPolicy: (ext: string) =>
    api.post('/images/upload-policy', null, { params: { ext } }),
  uploadDirect: async (file: File): Promise<string> => {
    const ext = file.name.includes('.') ? file.name.substring(file.name.lastIndexOf('.')) : '.jpg'

    let policyRes
    try {
      policyRes = await imageApi.uploadPolicy(ext)
    } catch (e: any) {
      const detail = e?.response?.data?.message || e?.message || '未知错误'
      throw new Error(`获取上传策略失败: ${detail}`)
    }

    const policy = policyRes.data?.data
    if (!policy) throw new Error('获取上传策略失败: 服务器返回数据异常')

    const fd = new FormData()
    fd.append('OSSAccessKeyId', policy.accessId)
    fd.append('policy', policy.policy)
    fd.append('signature', policy.signature)
    fd.append('key', policy.key)
    fd.append('success_action_status', '200')
    fd.append('file', file)

    let ossRes
    try {
      ossRes = await fetch(policy.host, { method: 'POST', body: fd })
    } catch (e: any) {
      console.error('[OSS Upload] fetch 失败', e)
      if (e?.message?.includes('Failed to fetch') || e?.name === 'TypeError') {
        throw new Error('OSS 上传网络异常，请检查网络或稍后重试')
      }
      throw new Error(`OSS 上传失败: ${e?.message || '网络错误'}`)
    }

    if (!ossRes.ok) {
      const body = await ossRes.text().catch(() => '')
      console.error(`[OSS Upload] HTTP ${ossRes.status}: ${body}`)
      throw new Error(`OSS 上传失败 (${ossRes.status})`)
    }
    return policy.url
  },
}

export interface OssPolicy {
  host: string
  accessId: string
  policy: string
  signature: string
  key: string
  url: string
  expireMs: number
}

export const musicApi = {
  parse: (url: string, text?: string) => api.post('/music/parse', { url, text: text || '' }),
  lyrics: (url: string, title: string, artist: string) => api.post('/music/lyrics', { url, title, artist }),
}

export const supportApi = {
  images: () => api.get('/support-images'),
  uploadImage: (type: string, file: File) => {
    const form = new FormData()
    form.append('type', type)
    form.append('file', file)
    return api.post('/admin/support-images', form)
  },
}

export const suggestionApi = {
  submit: (content: string) => api.post('/suggestions', { content }),
  adminList: (page = 1, size = 20) => api.get('/suggestions/admin', { params: { page, size } }),
}
