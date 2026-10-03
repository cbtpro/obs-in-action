/**
 * 后端统一返回结构
 */
export interface Result<T> {
  /** 业务状态码：0 表示成功 */
  code: number;
  message: string;
  data: T;
}
