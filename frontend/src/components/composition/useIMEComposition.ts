import { useEffect, useRef } from 'react'

type UseIMECompositionOptions<T, D = T> = {
  debounce?: number
  onChange?: (value: T) => void
  onDebouncedChange?: (value: D) => void
  toDebouncedValue?: (value: T) => D
}

/**
 * 管理 IME 合成的通用状态机。
 *
 * 组件只需在原生事件中调用 startComposition、handleChange 和 endComposition，
 * 不需要重复处理临时值缓存、去抖取消或提交时机。
 */
export function useIMEComposition<T, D = T>(options: UseIMECompositionOptions<T, D>) {
  const composingRef = useRef(false)
  const pendingValueRef = useRef<T | undefined>(undefined)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const optionsRef = useRef(options)

  // 始终使用最新回调，避免异步计时器闭包持有旧 props。
  optionsRef.current = options

  const clearDebounce = () => {
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = null
  }

  useEffect(() => clearDebounce, [])

  const commit = (value: T) => {
    const { onChange, onDebouncedChange, debounce = 0, toDebouncedValue } = optionsRef.current
    onChange?.(value)

    clearDebounce()
    if (!onDebouncedChange) return

    const debouncedValue = toDebouncedValue
      ? toDebouncedValue(value)
      : (value as unknown as D)

    if (!debounce) {
      onDebouncedChange(debouncedValue)
      return
    }

    timerRef.current = setTimeout(() => {
      optionsRef.current.onDebouncedChange?.(debouncedValue)
    }, debounce)
  }

  const startComposition = () => {
    composingRef.current = true
    pendingValueRef.current = undefined
    clearDebounce()
  }

  const handleChange = (value: T, nativeIsComposing = false) => {
    if (composingRef.current || nativeIsComposing) {
      pendingValueRef.current = value
      return
    }
    commit(value)
  }

  const endComposition = (...finalValue: [] | [T]) => {
    composingRef.current = false
    const value = finalValue.length ? finalValue[0] : pendingValueRef.current
    pendingValueRef.current = undefined
    if (value !== undefined) commit(value)
  }

  return { composingRef, startComposition, handleChange, endComposition }
}
