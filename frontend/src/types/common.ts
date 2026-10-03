/**
 * 通用分页响应（与服务端 PageDTO 对应）
 */
export interface PageResult<T> {
  /** 满足条件的总条数 */
  total: number;
  /** 当前页数据 */
  records: T[];
}

/**
 * 通用分页查询参数
 */
export interface PageParams {
  page?: number;
  pageSize?: number;
}
