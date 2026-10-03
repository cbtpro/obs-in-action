import { Input } from 'antd'
import type { InputProps } from 'antd'
import { withComposition } from './composition/withComposition'

/** 支持输入法合成的 Ant Design Input。 */
export const IMEInput = withComposition<HTMLInputElement, InputProps>(Input)
