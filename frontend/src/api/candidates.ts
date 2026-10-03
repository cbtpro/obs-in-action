import http from './client';
import { Candidate, CandidateFormValues } from '../types/candidate';
import { PageParams, PageResult } from '../types/common';

/**
 * 面试候选人接口封装。
 * 所有方法返回 CancellablePromise，调用方可在 useEffect cleanup 中调 cancel()。
 */

/** 候选人列表查询参数 */
export interface CandidateListParams extends PageParams {
  name?: string;
  idCard?: string;
  gender?: string;
}

/**
 * 分页列表（支持按姓名、身份证号模糊搜索 + 性别过滤）。
 * 查询条件含身份证号等敏感信息，通过 POST 请求体传递，避免出现在 URL 中。
 */
export function listCandidates(params: CandidateListParams = {}) {
  return http.post<PageResult<Candidate>>('/candidates/list', {
    name: params.name,
    idCard: params.idCard,
    gender: params.gender,
    page: params.page,
    pageSize: params.pageSize,
  });
}

/** 详情 */
export function getCandidate(id: number) {
  return http.get<Candidate>(`/candidates/${id}`);
}

/** 新增 */
export function createCandidate(form: CandidateFormValues) {
  const fd = new FormData();
  fd.append('name', form.name);
  fd.append('idCard', form.idCard);
  if (form.bio) fd.append('bio', form.bio);
  if (form.photo) fd.append('photo', form.photo);
  if (form.resume) fd.append('resume', form.resume);
  return http.post<Candidate>('/candidates', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 修改 */
export function updateCandidate(id: number, form: Partial<CandidateFormValues>) {
  const fd = new FormData();
  if (form.name !== undefined) fd.append('name', form.name);
  if (form.idCard !== undefined) fd.append('idCard', form.idCard);
  if (form.bio !== undefined) fd.append('bio', form.bio ?? '');
  if (form.photo) fd.append('photo', form.photo);
  if (form.resume) fd.append('resume', form.resume);
  return http.put<Candidate>(`/candidates/${id}`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 删除 */
export function deleteCandidate(id: number) {
  return http.delete<void>(`/candidates/${id}`);
}
