import { InputNumber } from 'antd'
import type { InputNumberProps } from 'antd'
import { useIMEComposition } from './composition/useIMEComposition'

type NumericValue = string | number

type IMENumberInputProps<T extends NumericValue = NumericValue> = InputNumberProps<T> & {
  /** 延迟执行 onDebouncedChange 的时间（毫秒），默认为不延迟。 */
  debounce?: number
  /** 适合搜索、远程校验、自动保存等非表单同步操作。 */
  onDebouncedChange?: (value: T | null) => void
}

/**
 * InputNumber 的 IME 包装组件。
 *
 * 数字解析、精度、formatter、parser、stringMode 等能力全部交给 Ant Design InputNumber；
 * 本组件只避免合成过程中的临时值提前写入 Form。
 */
export function IMENumberInput<T extends NumericValue = NumericValue>({
  debounce = 0,
  onChange,
  onDebouncedChange,
  onCompositionStart,
  onCompositionEnd,
  ...props
}: IMENumberInputProps<T>) {
  const { startComposition, handleChange, endComposition } = useIMEComposition<T | null>({
    debounce,
    onChange,
    onDebouncedChange,
  })

  return (
    <InputNumber<T>
      {...props}
      onCompositionStart={(event) => {
        startComposition()
        onCompositionStart?.(event)
      }}
      onCompositionEnd={(event) => {
        endComposition()
        onCompositionEnd?.(event)
      }}
      onChange={(nextValue) => {
        handleChange(nextValue)
      }}
    />
  )
}
