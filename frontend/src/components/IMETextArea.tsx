import { Input } from 'antd'
import type { TextAreaProps } from 'antd/es/input'
import { withComposition } from './composition/withComposition'

/** 使用同一套合成逻辑扩展出的 Ant Design Input.TextArea。 */
export const IMETextArea = withComposition<HTMLTextAreaElement, TextAreaProps>(Input.TextArea)
