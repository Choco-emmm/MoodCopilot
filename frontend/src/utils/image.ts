/** 浏览器端 Canvas 压缩图片，返回 File。默认压缩到 ≤500KB。 */
export async function compressImage(file: File, maxKB = 500): Promise<File> {
  if (file.size <= maxKB * 1024) return file

  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      URL.revokeObjectURL(url)
      const { width, height } = scaleDown(img.width, img.height, 2048)
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      if (!ctx) { resolve(file); return }
      ctx.drawImage(img, 0, 0, width, height)

      // 二分查找合适的 quality
      let lo = 0.1, hi = 1.0, best: Blob | null = null
      const tryQuality = (q: number) => {
        return new Promise<Blob>((res) => canvas.toBlob((b) => res(b!), file.type, q))
      }
      const step = async () => {
        for (let round = 0; round < 6; round++) {
          const mid = (lo + hi) / 2
          const blob = await tryQuality(mid)
          if (blob.size <= maxKB * 1024) {
            best = blob
            lo = mid
          } else {
            hi = mid
          }
        }
        if (best && best.size < file.size) {
          resolve(new File([best], file.name, { type: file.type }))
        } else {
          resolve(file)
        }
      }
      step().catch(() => resolve(file))
    }
    img.onerror = () => { URL.revokeObjectURL(url); resolve(file) }
    img.src = url
  })
}

function scaleDown(w: number, h: number, maxSide: number) {
  if (w <= maxSide && h <= maxSide) return { width: w, height: h }
  const ratio = maxSide / Math.max(w, h)
  return { width: Math.round(w * ratio), height: Math.round(h * ratio) }
}
