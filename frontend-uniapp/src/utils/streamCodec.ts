/**
 * 分片流的纯逻辑编解码：把 ArrayBuffer 分片还原成字符串，再把字符串还原成 SSE 帧。
 * 刻意不依赖 uni/wx 任何 API，方便在 Node 里直接单测。
 */

const REPLACEMENT = String.fromCharCode(0xfffd)

/** 首字节对应的序列总长度；非法首字节返回 0 */
function sequenceLength(byte: number): number {
  if (byte < 0x80) return 1
  if ((byte & 0xe0) === 0xc0) return 2
  if ((byte & 0xf0) === 0xe0) return 3
  if ((byte & 0xf8) === 0xf0) return 4
  return 0
}

const isContinuation = (byte: number) => (byte & 0xc0) === 0x80

/** 各长度下合法码点的下界，用来排除 overlong 编码 */
const MIN_CODE_POINT = [0, 0, 0x80, 0x800, 0x10000]

function codePointAt(bytes: Uint8Array, offset: number, length: number): number {
  if (length === 1) return bytes[offset]
  if (length === 2) {
    return ((bytes[offset] & 0x1f) << 6) | (bytes[offset + 1] & 0x3f)
  }
  if (length === 3) {
    return ((bytes[offset] & 0x0f) << 12)
      | ((bytes[offset + 1] & 0x3f) << 6)
      | (bytes[offset + 2] & 0x3f)
  }
  return ((bytes[offset] & 0x07) << 18)
    | ((bytes[offset + 1] & 0x3f) << 12)
    | ((bytes[offset + 2] & 0x3f) << 6)
    | (bytes[offset + 3] & 0x3f)
}

export interface Utf8Decoder {
  push(bytes: ArrayBuffer | Uint8Array): string
  flush(): string
}

/**
 * 有状态 UTF-8 解码器。小程序环境没有 TextDecoder，而中文（3 字节）和 emoji（4 字节）
 * 会被切在分片边界上，必须把不完整的尾部字节留到下一片。
 */
export function createUtf8Decoder(): Utf8Decoder {
  let pending = new Uint8Array(0)

  return {
    push(input) {
      const chunk = input instanceof Uint8Array ? input : new Uint8Array(input)
      if (!chunk.length) return ''

      let bytes: Uint8Array
      if (pending.length) {
        bytes = new Uint8Array(pending.length + chunk.length)
        bytes.set(pending, 0)
        bytes.set(chunk, pending.length)
        pending = new Uint8Array(0)
      } else {
        bytes = chunk
      }

      let out = ''
      let i = 0
      while (i < bytes.length) {
        const length = sequenceLength(bytes[i])
        if (length === 0) {
          out += REPLACEMENT
          i += 1
          continue
        }
        if (i + length > bytes.length) {
          // 还没凑齐整个序列。先看到手的续字节是否已经违规，
          // 能立刻判定非法就不必等到下一片（也不该把它留进 pending）。
          let mayStillComplete = true
          for (let k = i + 1; k < bytes.length; k += 1) {
            if (!isContinuation(bytes[k])) {
              mayStillComplete = false
              break
            }
          }
          if (mayStillComplete) {
            pending = bytes.slice(i) // 尾部不完整，最多 3 字节
            break
          }
          out += REPLACEMENT
          i += 1
          continue
        }

        let complete = true
        for (let k = 1; k < length; k += 1) {
          if (!isContinuation(bytes[i + k])) {
            complete = false
            break
          }
        }
        const codePoint = complete ? codePointAt(bytes, i, length) : -1
        const invalid = !complete
          || codePoint < MIN_CODE_POINT[length]
          || codePoint > 0x10ffff
          || (codePoint >= 0xd800 && codePoint <= 0xdfff)

        if (invalid) {
          // 只前进 1 字节，让后面的字节重新参与解析
          out += REPLACEMENT
          i += 1
          continue
        }

        out += String.fromCodePoint(codePoint)
        i += length
      }

      return out
    },

    flush() {
      if (!pending.length) return ''
      pending = new Uint8Array(0)
      return REPLACEMENT
    },
  }
}

export interface SseParser {
  push(text: string): void
  flush(): void
}

/**
 * SSE 帧解析器。服务端（Spring MVC + SseEmitter）写出的是 `data:{json}\n\n`：
 * 冒号后没有空格、没有 event/id 字段、没有心跳注释。
 */
export function createSseParser(onFrame: (data: string) => void): SseParser {
  let buffer = ''

  const emit = (rawFrame: string) => {
    const dataLines: string[] = []
    for (const rawLine of rawFrame.split('\n')) {
      const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
      if (!line || line.startsWith(':')) continue // 空行 / 注释
      const colon = line.indexOf(':')
      const field = colon === -1 ? line : line.slice(0, colon)
      if (field !== 'data') continue // 忽略 event: / id: / retry:
      let value = colon === -1 ? '' : line.slice(colon + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      dataLines.push(value)
    }
    if (dataLines.length) onFrame(dataLines.join('\n'))
  }

  return {
    push(text) {
      buffer += text.replace(/\r\n/g, '\n')
      let index = buffer.indexOf('\n\n')
      while (index !== -1) {
        emit(buffer.slice(0, index))
        buffer = buffer.slice(index + 2)
        index = buffer.indexOf('\n\n')
      }
    },

    flush() {
      // 连接在 done 帧之后立即关闭时，最后一帧可能没有结尾空行
      const rest = buffer
      buffer = ''
      if (rest.trim()) emit(rest)
    },
  }
}
