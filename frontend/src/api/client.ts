import { HttpClient } from './HttpClient';

/**
 * 全局 HTTP 客户端单例。
 *
 * 业务 API 层统一通过此实例发起请求，自动获得：
 * - Result 自动解包
 * - AbortController 管理（cancel）
 * - 403 跳转无权限页
 * - 全局错误提示
 */
export const http = new HttpClient();

export default http;
