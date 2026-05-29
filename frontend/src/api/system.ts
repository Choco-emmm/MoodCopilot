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
    const policyRes = await imageApi.uploadPolicy(ext)
    const policy = policyRes.data?.data
    if (!policy) throw new Error('获取上传策略失败')

    const fd = new FormData()
    fd.append('OSSAccessKeyId', policy.accessId)
    fd.append('policy', policy.policy)
    fd.append('signature', policy.signature)
    fd.append('key', policy.key)
    fd.append('success_action_status', '200')
    fd.append('file', file)

    const ossRes = await fetch(policy.host, { method: 'POST', body: fd })
    if (!ossRes.ok) throw new Error('图片上传失败')
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
