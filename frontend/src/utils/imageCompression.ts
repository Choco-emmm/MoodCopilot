/**
 * 图片上传前的压缩，按「文字图 / 普通图」分通道处理。
 *
 * 文字图（截图、海报、单据）保留 PNG 或高质量 WebP，尽量不损伤文字可读性 ——
 * 后续的 OCR 依赖这些像素；普通照片转 WebP 优先压体积。
 *
 * 日记上传与聊天发图共用这套策略：同一种图从不同入口进来，不该得到不同质量。
 */

export type UploadChannel = 'normal' | 'text'

export interface UploadImageAnalysis {
  width: number
  height: number
  edgeDensity: number
  grayRatio: number
}

export interface PreparedImageUpload {
  file: File
  channel: UploadChannel
  origWidth: number
  origHeight: number
  compressedWidth: number
  compressedHeight: number
  origSize: number
  compressedSize: number
  quality?: number
  mime: string
}

/** 聊天发图只需要压好的文件本身，不需要日记那套上传元数据。 */
export async function compressForChat(file: File): Promise<File> {
  const prepared = await prepareImageUpload(file)
  return prepared.file
}

export async function prepareImageUpload(file: File): Promise<PreparedImageUpload> {
  const unsupportedCompressTypes = ['image/gif', 'image/heic', 'image/heif']
  if (unsupportedCompressTypes.includes(file.type)) {
    const dim = await readImageDimensions(file)
    return {
      file,
      channel: file.type === 'image/png' ? 'text' : 'normal',
      origWidth: dim.width,
      origHeight: dim.height,
      compressedWidth: dim.width,
      compressedHeight: dim.height,
      origSize: file.size,
      compressedSize: file.size,
      mime: file.type || 'image/jpeg',
    }
  }

  const analysis = await analyzeImage(file)
  const channel = classifyChannel(file, analysis)
  const policy = getCompressionPolicy(channel, file.type)
  const compressed = await compressImage(file, analysis.width, analysis.height, policy.targetLongEdge, policy.mime, policy.quality)

  const shouldFallback = !compressed || compressed.blob.size >= file.size * 0.98
  if (shouldFallback) {
    return {
      file,
      channel,
      origWidth: analysis.width,
      origHeight: analysis.height,
      compressedWidth: analysis.width,
      compressedHeight: analysis.height,
      origSize: file.size,
      compressedSize: file.size,
      mime: file.type || 'image/jpeg',
    }
  }

  const uploadFile = blobToFile(compressed.blob, file.name, compressed.mime)
  return {
    file: uploadFile,
    channel,
    origWidth: analysis.width,
    origHeight: analysis.height,
    compressedWidth: compressed.width,
    compressedHeight: compressed.height,
    origSize: file.size,
    compressedSize: uploadFile.size,
    quality: compressed.quality,
    mime: compressed.mime,
  }
}

function classifyChannel(file: File, analysis: UploadImageAnalysis): UploadChannel {
  const ratio = analysis.width / Math.max(1, analysis.height)
  const isPng = file.type === 'image/png'
  const screenshotAspect = (ratio > 0.45 && ratio < 0.62) || (ratio > 1.6 && ratio < 2.3)
  const denseEdges = analysis.edgeDensity > 0.14
  const veryDenseEdges = analysis.edgeDensity > 0.18
  const highGray = analysis.grayRatio > 0.5
  const mediumGray = analysis.grayRatio > 0.38
  const compactImage = Math.min(analysis.width, analysis.height) < 1500

  if (isPng) {
    return denseEdges || mediumGray || screenshotAspect ? 'text' : 'normal'
  }

  if (screenshotAspect && (denseEdges || mediumGray)) {
    return 'text'
  }

  if (compactImage && veryDenseEdges && highGray) {
    return 'text'
  }

  return 'normal'
}

function getCompressionPolicy(channel: UploadChannel, sourceMime: string) {
  if (channel === 'text') {
    return {
      targetLongEdge: 2048,
      quality: sourceMime === 'image/png' ? undefined : 0.9,
      mime: sourceMime === 'image/png' ? 'image/png' : 'image/webp',
    }
  }
  return {
    targetLongEdge: 1800,
    quality: 0.84,
    mime: 'image/webp',
  }
}

async function analyzeImage(file: File): Promise<UploadImageAnalysis> {
  const { img, revoke } = await loadImage(file)
  try {
    const sample = 96
    const canvas = document.createElement('canvas')
    canvas.width = sample
    canvas.height = sample
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) {
      return { width: img.naturalWidth, height: img.naturalHeight, edgeDensity: 0, grayRatio: 0 }
    }

    ctx.drawImage(img, 0, 0, sample, sample)
    const data = ctx.getImageData(0, 0, sample, sample).data
    let grayPixels = 0
    let edgePixels = 0
    const luminance = new Float32Array(sample * sample)

    for (let i = 0, p = 0; i < data.length; i += 4, p++) {
      const r = data[i]
      const g = data[i + 1]
      const b = data[i + 2]
      luminance[p] = 0.299 * r + 0.587 * g + 0.114 * b
      if (Math.abs(r - g) < 12 && Math.abs(g - b) < 12) grayPixels++
    }

    for (let y = 1; y < sample - 1; y++) {
      for (let x = 1; x < sample - 1; x++) {
        const idx = y * sample + x
        const dx = Math.abs(luminance[idx + 1] - luminance[idx - 1])
        const dy = Math.abs(luminance[idx + sample] - luminance[idx - sample])
        if (dx + dy > 35) edgePixels++
      }
    }

    const total = sample * sample
    return {
      width: img.naturalWidth,
      height: img.naturalHeight,
      edgeDensity: edgePixels / total,
      grayRatio: grayPixels / total,
    }
  } finally {
    revoke()
  }
}

async function readImageDimensions(file: File): Promise<{ width: number; height: number }> {
  const { img, revoke } = await loadImage(file)
  try {
    return { width: img.naturalWidth, height: img.naturalHeight }
  } finally {
    revoke()
  }
}

async function loadImage(file: File): Promise<{ img: HTMLImageElement; revoke: () => void }> {
  const objectUrl = URL.createObjectURL(file)
  const img = new Image()
  img.decoding = 'async'
  await new Promise<void>((resolve, reject) => {
    img.onload = () => resolve()
    img.onerror = () => reject(new Error('读取图片失败'))
    img.src = objectUrl
  })
  return {
    img,
    revoke: () => URL.revokeObjectURL(objectUrl),
  }
}

async function compressImage(
  file: File,
  sourceWidth: number,
  sourceHeight: number,
  targetLongEdge: number,
  outputMime: string,
  quality?: number,
): Promise<{ blob: Blob; width: number; height: number; mime: string; quality?: number } | null> {
  const { img, revoke } = await loadImage(file)
  try {
    const longEdge = Math.max(sourceWidth, sourceHeight)
    const scale = Math.min(1, targetLongEdge / Math.max(1, longEdge))
    const width = Math.max(1, Math.round(sourceWidth * scale))
    const height = Math.max(1, Math.round(sourceHeight * scale))

    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) return null

    ctx.drawImage(img, 0, 0, width, height)
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob((b) => resolve(b), outputMime, quality)
    })
    if (!blob) return null
    return { blob, width, height, mime: outputMime, quality }
  } finally {
    revoke()
  }
}

function blobToFile(blob: Blob, originalName: string, mime: string): File {
  const base = originalName.includes('.') ? originalName.slice(0, originalName.lastIndexOf('.')) : originalName
  const ext = mime === 'image/png' ? '.png' : mime === 'image/webp' ? '.webp' : '.jpg'
  return new File([blob], `${base}${ext}`, { type: mime, lastModified: Date.now() })
}
