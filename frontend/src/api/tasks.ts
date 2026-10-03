import type { TaskStatus, SeedTaskParams, ClearResult, CountResult } from '../types/task';
import type { PageResult } from '../types/common';
import { HttpClient } from './HttpClient';

/**
 * 任务管理 API 客户端。
 * 指向 task-service 微服务（端口 8081），通过 Vite proxy /task-api 转发。
 */
const http = new HttpClient({
  baseURL: '/task-api/api',
  onError: (msg) => console.error('[Task API]', msg),
  onForbidden: () => console.warn('[Task API] 403'),
});

/** 注册播种任务 */
export function registerSeedTask(params: SeedTaskParams): CancellablePromise<TaskStatus> {
  return http.post('/tasks/seed', params);
}

/** 立即触发任务 */
export function triggerTask(taskId: string): CancellablePromise<void> {
  return http.post(`/tasks/${taskId}/trigger`);
}

/** 停止任务（记录断点，可恢复） */
export function stopTask(taskId: string): CancellablePromise<void> {
  return http.post(`/tasks/${taskId}/stop`);
}

/** 恢复（继续）已停止的任务 */
export function resumeTask(taskId: string): CancellablePromise<void> {
  return http.post(`/tasks/${taskId}/resume`);
}

/** 查询任务状态 */
export function getTaskStatus(taskId: string): CancellablePromise<TaskStatus> {
  return http.get(`/tasks/${taskId}/status`);
}

/** 任务列表查询参数（关键字模糊匹配任务 ID / 名称 / 数据标志） */
export interface TaskListParams {
  keyword?: string;
  page?: number;
  pageSize?: number;
}

/** 按条件分页列出任务（POST 请求体传参） */
export function listTasks(params: TaskListParams = {}): CancellablePromise<PageResult<TaskStatus>> {
  return http.post('/tasks/list', {
    keyword: params.keyword,
    page: params.page,
    pageSize: params.pageSize,
  });
}

/** 移除任务 */
export function removeTask(taskId: string): CancellablePromise<void> {
  return http.delete(`/tasks/${taskId}`);
}

/** 按标志清理模拟数据 */
export function clearMockData(mockTag: string): CancellablePromise<ClearResult> {
  return http.delete(`/tasks/mock-data/${mockTag}`);
}

/** 按标志统计模拟数据 */
export function countMockData(mockTag: string): CancellablePromise<CountResult> {
  return http.get(`/tasks/mock-data/${mockTag}/count`);
}

import type { CancellablePromise } from './HttpClient';
