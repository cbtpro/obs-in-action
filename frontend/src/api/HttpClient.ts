import axios, {
  AxiosError,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios';
import { message } from 'antd';
import type { Result } from '../types/api';

/**
 * 可取消的 Promise：在标准 Promise 上附加 cancel 方法。
 * 调用方可在 useEffect cleanup 中调用 cancel() 终止请求。
 */
export interface CancellablePromise<T> extends Promise<T> {
  /** 取消底层 HTTP 请求 */
  cancel: () => void;
}

/**
 * HttpClient 配置项。
 * 通过 config 注入策略，不修改类本身（开闭原则）。
 */
export interface HttpClientConfig {
  baseURL?: string;
  timeout?: number;
  /** 403 处理策略，默认跳转 /403 */
  onForbidden?: () => void;
  /** 全局错误提示策略，默认 message.error */
  onError?: (msg: string) => void;
}

/**
 * HTTP 客户端：统一封装 Result 解包、AbortController 管理、403 跳转、全局异常。
 *
 * 设计要点（开闭原则）：
 * - 业务 API 层只需调用 http.get/post/put/delete，无需关心 signal、错误处理、Result 解包
 * - 横切关注点（403、错误提示）通过 config 注入策略，可替换不影响已有代码
 * - 新增请求类型在此类中追加方法即可，不修改已有 API 模块
 * - 新增横切关注点可在 setupInterceptors 中追加拦截器，不修改已有请求方法
 */
export class HttpClient {
  private readonly instance: ReturnType<typeof axios.create>;
  private readonly onForbidden: () => void;
  private readonly onError: (msg: string) => void;

  constructor(config: HttpClientConfig = {}) {
    this.instance = axios.create({
      baseURL: config.baseURL ?? '/api',
      timeout: config.timeout ?? 60_000,
    });
    this.onForbidden = config.onForbidden ?? (() => {
      window.location.href = '/403';
    });
    this.onError = config.onError ?? ((msg: string) => message.error(msg));
    this.setupInterceptors();
  }

  /** 响应拦截器：Result 解包 + 403 跳转 + 全局错误提示 */
  private setupInterceptors(): void {
    this.instance.interceptors.response.use(
      (resp: AxiosResponse) => {
        const body = resp.data;
        // 非标准 Result（如文件流）直接返回
        if (body == null || typeof body !== 'object' || !('code' in body)) {
          return resp;
        }
        const result = body as Result<unknown>;
        if (result.code === 0) {
          // 解包：让调用方直接拿到 data
          return { ...resp, data: result.data };
        }
        // 业务错误
        return Promise.reject(new Error(result.message || '请求失败'));
      },
      (error: AxiosError<Result<unknown>>) => {
        // AbortController 取消，静默处理
        if (error.code === 'ERR_CANCELED') {
          return Promise.reject(error);
        }
        // 403 跳转无权限页
        if (error.response?.status === 403) {
          this.onForbidden();
          return Promise.reject(error);
        }
        // 全局错误提示
        const msg = error.response?.data?.message || error.message || '网络异常';
        this.onError(msg);
        return Promise.reject(error);
      },
    );
  }

  /** GET 请求，返回可取消的 Promise */
  get<T>(url: string, config?: AxiosRequestConfig): CancellablePromise<T> {
    return this.request<T>('GET', url, config);
  }

  /** POST 请求 */
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): CancellablePromise<T> {
    return this.request<T>('POST', url, { ...config, data });
  }

  /** PUT 请求 */
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): CancellablePromise<T> {
    return this.request<T>('PUT', url, { ...config, data });
  }

  /** DELETE 请求 */
  delete<T>(url: string, config?: AxiosRequestConfig): CancellablePromise<T> {
    return this.request<T>('DELETE', url, config);
  }

  /**
   * 底层请求方法：创建 AbortController 并附加 cancel 到 Promise。
   * 调用方可通过 cancel() 终止请求，用于 useEffect cleanup。
   */
  private request<T>(
    method: string,
    url: string,
    config?: AxiosRequestConfig,
  ): CancellablePromise<T> {
    const controller = new AbortController();
    const axiosPromise = this.instance
      .request<unknown, AxiosResponse<T>, InternalAxiosRequestConfig<unknown>>({
        method,
        url,
        ...config,
        signal: controller.signal,
      })
      .then((resp) => resp.data);

    // 将 cancel 方法附加到 Promise 对象上
    return Object.assign(axiosPromise, {
      cancel: () => controller.abort(),
    }) as CancellablePromise<T>;
  }
}
