/** 任务运行状态 */
export interface TaskStatus {
  taskId: string;
  name: string;
  running: boolean;
  stopRequested: boolean;
  total: number;
  processed: number;
  failed: number;
  batchCount: number;
  startTime: string | null;
  endTime: string | null;
  message: string | null;
  /** 数据标志，用于清理模拟数据 */
  mockTag: string | null;
}

/** 注册播种任务请求参数 */
export interface SeedTaskParams {
  name?: string;
  total: number;
  batchSize: number;
  intervalMillis: number;
  cronExpression?: string;
  mockTag?: string;
}

/** 清理模拟数据结果 */
export interface ClearResult {
  deleted: number;
  mockTag: string;
}

/** 统计模拟数据结果 */
export interface CountResult {
  count: number;
  mockTag: string;
}
