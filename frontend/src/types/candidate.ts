/**
 * 面试候选人
 */
export interface Candidate {
  id: number;
  name: string;
  idCard: string;
  gender: string | null;
  age: number | null;
  /** 照片代理地址，如 /api/files/{key} */
  photoUrl: string | null;
  /** 照片原始文件名 */
  photoName: string | null;
  /** 简历代理地址 */
  resumeUrl: string | null;
  /** 简历原始文件名 */
  resumeName: string | null;
  bio: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

/**
 * 候选人表单数据（新增/编辑）
 */
export interface CandidateFormValues {
  name: string;
  idCard: string;
  bio?: string;
  photo?: File;
  resume?: File;
}
