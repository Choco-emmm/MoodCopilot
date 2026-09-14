import { describe, expect, it } from 'vitest'
import { createSseParser, createUtf8Decoder } from '../streamCodec'

const REPLACEMENT = String.fromCharCode(0xfffd)
const encode = (text: string) => new TextEncoder().encode(text)

/** 把字节数组按第 offset 个位置切成两片喂进去 */
function decodeSplitAt(bytes: Uint8Array, offset: number): string {
  const decoder = createUtf8Decoder()
  return decoder.push(bytes.slice(0, offset)) + decoder.push(bytes.slice(offset)) + decoder.flush()
}

describe('createUtf8Decoder', () => {
  const samples = [
    'hello world',
    '中文测试：芋泥奶奶蛋黄酥',
    '混合 mixed 中文 😀🎉 emoji',
    '带标点的句子，。！？——"引号"',
    '🏳️‍🌈 组合 emoji 与零宽连接符',
  ]

  it('在每个字节边界切断后仍能还原原文', () => {
    for (const text of samples) {
      const bytes = encode(text)
      for (let offset = 0; offset <= bytes.length; offset += 1) {
        expect(decodeSplitAt(bytes, offset)).toBe(text)
      }
    }
  })

  it('逐字节喂入仍能还原原文', () => {
    for (const text of samples) {
      const bytes = encode(text)
      const decoder = createUtf8Decoder()
      let out = ''
      for (const byte of bytes) {
        out += decoder.push(new Uint8Array([byte]))
      }
      out += decoder.flush()
      expect(out).toBe(text)
    }
  })

  it('接受 ArrayBuffer 与 Uint8Array 两种入参', () => {
    const bytes = encode('中文')
    const asArrayBuffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)
    expect(createUtf8Decoder().push(asArrayBuffer as ArrayBuffer)).toBe('中文')
    expect(createUtf8Decoder().push(bytes)).toBe('中文')
  })

  it('非法序列不抛异常且产出替换字符', () => {
    expect(() => createUtf8Decoder().push(new Uint8Array([0xe4, 0xff]))).not.toThrow()
    expect(createUtf8Decoder().push(new Uint8Array([0xe4, 0xff]))).toContain(REPLACEMENT)

    // 游离续字节
    expect(createUtf8Decoder().push(new Uint8Array([0x80, 0x41]))).toBe(`${REPLACEMENT}A`)
    // overlong 编码（0xC0 0x80 表示 NUL）
    expect(createUtf8Decoder().push(new Uint8Array([0xc0, 0x80]))).toBe(REPLACEMENT + REPLACEMENT)
    // 代理区码点
    expect(createUtf8Decoder().push(new Uint8Array([0xed, 0xa0, 0x80]))).toBe(REPLACEMENT + REPLACEMENT + REPLACEMENT)
  })

  it('截断的尾部在 flush 时补替换字符', () => {
    const decoder = createUtf8Decoder()
    expect(decoder.push(new Uint8Array([0xe4, 0xb8]))).toBe('')
    expect(decoder.flush()).toBe(REPLACEMENT)
  })

  it('空输入不产生输出', () => {
    const decoder = createUtf8Decoder()
    expect(decoder.push(new Uint8Array(0))).toBe('')
    expect(decoder.flush()).toBe('')
  })
})

/** 把字符串按第 offset 个位置切两片喂进解析器，返回收到的所有帧 */
function parseSplitAt(text: string, offset: number): string[] {
  const frames: string[] = []
  const parser = createSseParser(frame => frames.push(frame))
  parser.push(text.slice(0, offset))
  parser.push(text.slice(offset))
  parser.flush()
  return frames
}

describe('createSseParser', () => {
  const stream =
    'data:{"content":"你","type":"chunk","seq":1}\n\n'
    + 'data:{"type":"references","items":[{"type":"diary","diaryId":"7"}]}\n\n'
    + 'data:{"content":"好","type":"chunk","seq":2}\n\n'
    + 'data:{"type":"done","seq":3}\n\n'

  it('在每个字符边界切断后仍能还原所有帧', () => {
    const expected = [
      '{"content":"你","type":"chunk","seq":1}',
      '{"type":"references","items":[{"type":"diary","diaryId":"7"}]}',
      '{"content":"好","type":"chunk","seq":2}',
      '{"type":"done","seq":3}',
    ]
    for (let offset = 0; offset <= stream.length; offset += 1) {
      expect(parseSplitAt(stream, offset)).toEqual(expected)
    }
  })

  it('解析出的事件载荷可以被 JSON.parse', () => {
    const frames = parseSplitAt(stream, 0)
    expect(frames.map(f => JSON.parse(f).type)).toEqual(['chunk', 'references', 'chunk', 'done'])
  })

  it('结尾没有空行的最后一帧也会被 flush 送出', () => {
    const frames: string[] = []
    const parser = createSseParser(frame => frames.push(frame))
    parser.push('data:{"type":"done","seq":1}')
    expect(frames).toEqual([])
    parser.flush()
    expect(frames).toEqual(['{"type":"done","seq":1}'])
  })

  it('兼容 CRLF 行尾', () => {
    const frames: string[] = []
    const parser = createSseParser(frame => frames.push(frame))
    parser.push('data:{"type":"done","seq":1}\r\n\r\n')
    expect(frames).toEqual(['{"type":"done","seq":1}'])
  })

  it('忽略注释行与 event/id/retry 字段', () => {
    const frames: string[] = []
    const parser = createSseParser(frame => frames.push(frame))
    parser.push(':ping\n\n')
    parser.push('event: message\nid: 42\nretry: 100\ndata:{"type":"done","seq":2}\n\n')
    expect(frames).toEqual(['{"type":"done","seq":2}'])
  })

  it('data 冒号后的空格可有可无', () => {
    expect(parseSplitAt('data: {"type":"done"}\n\n', 0)).toEqual(['{"type":"done"}'])
    expect(parseSplitAt('data:{"type":"done"}\n\n', 0)).toEqual(['{"type":"done"}'])
  })

  it('JSON 内部转义的换行不会破坏分帧', () => {
    // Jackson 会把字符串里的换行写成两个字符 \ 和 n，帧内不会出现裸换行
    const body = '{"content":"第一行\\n第二行","type":"chunk","seq":1}'
    expect(parseSplitAt(`data:${body}\n\n`, 0)).toEqual([body])
    expect(JSON.parse(body).content).toBe('第一行\n第二行')
  })

  it('多行 data 用换行拼接', () => {
    expect(parseSplitAt('data:第一行\ndata:第二行\n\n', 0)).toEqual(['第一行\n第二行'])
  })

  it('空帧不触发回调', () => {
    const frames: string[] = []
    const parser = createSseParser(frame => frames.push(frame))
    parser.push('\n\n\n\n')
    parser.flush()
    expect(frames).toEqual([])
  })
})
