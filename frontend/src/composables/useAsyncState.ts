import { ref, type Ref } from 'vue'

interface AsyncState<T> {
  data: Ref<T>
  loading: Ref<boolean>
  error: Ref<string | null>
  run: (fn: () => Promise<T>) => Promise<T | null>
  reset: () => void
}

/**
 * Generic async wrapper — eliminates scattered loading/error ref boilerplate.
 *
 * @example
 * const { data: diaries, loading, error, run } = useAsyncState<Diary[]>([])
 * onMounted(() => run(() => diaryApi.mine().then(r => r.data.data)))
 */
export function useAsyncState<T>(initial: T): AsyncState<T> {
  const data = ref(initial) as Ref<T>
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function run(fn: () => Promise<T>): Promise<T | null> {
    loading.value = true
    error.value = null
    try {
      const result = await fn()
      data.value = result
      return result
    } catch (e: any) {
      error.value = e?.response?.data?.message || e?.message || '操作失败'
      return null
    } finally {
      loading.value = false
    }
  }

  function reset() {
    data.value = initial
    loading.value = false
    error.value = null
  }

  return { data, loading, error, run, reset }
}
