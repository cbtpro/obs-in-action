import type React from 'react'
import { useEffect, useRef, useState } from 'react'
import { useIMEComposition } from './useIMEComposition'

type TextLikeElement = HTMLInputElement | HTMLTextAreaElement

type TextLikeProps<E extends TextLikeElement> = {
  value?: unknown
  onChange?: React.ChangeEventHandler<E>
  onCompositionStart?: React.CompositionEventHandler<E>
  onCompositionEnd?: React.CompositionEventHandler<E>
}

export type WithCompositionProps = {
  /** 延迟执行 onDebouncedChange 的时间（毫秒）。 */
  debounce?: number
  /** 适合搜索、远程校验、自动保存等非表单同步操作。 */
  onDebouncedChange?: (value: string) => void
}

/** 为受控文本类组件增加 IME 合成支持。 */
export function withComposition<E extends TextLikeElement, P extends TextLikeProps<E>>(
  Component: React.ComponentType<P>,
) {
  function WithComposition({
    value,
    onChange,
    onCompositionStart,
    onCompositionEnd,
    debounce,
    onDebouncedChange,
    ...props
  }: P & WithCompositionProps) {
    const { composingRef, startComposition, handleChange, endComposition } = useIMEComposition<
      React.ChangeEvent<E>,
      string
    >({
      debounce,
      onChange,
      onDebouncedChange,
      toDebouncedValue: (event) => event.target.value,
    })
    const [inputValue, setInputValue] = useState(String(value ?? ''))
    const previousExternalValue = useRef(value)

    useEffect(() => {
      if (value !== previousExternalValue.current) {
        previousExternalValue.current = value
        if (!composingRef.current) setInputValue(String(value ?? ''))
      }
    }, [value, composingRef])

    return (
      <Component
        {...(props as P)}
        value={inputValue}
        onCompositionStart={(event) => {
          startComposition()
          onCompositionStart?.(event)
        }}
        onCompositionEnd={(event) => {
          setInputValue(event.currentTarget.value)
          endComposition(event as unknown as React.ChangeEvent<E>)
          onCompositionEnd?.(event)
        }}
        onChange={(event) => {
          setInputValue(event.target.value)
          handleChange(event, (event.nativeEvent as InputEvent).isComposing)
        }}
      />
    )
  }

  return WithComposition
}
