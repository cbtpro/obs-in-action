import { useEffect, useState } from 'react';
import { Modal, Spin } from 'antd';
import CandidateForm from './CandidateForm';
import { getCandidate } from '../api/candidates';
import { Candidate } from '../types/candidate';

interface Props {
  candidateId: number | null;
  open: boolean;
  onClose: () => void;
}

/**
 * 查看候选人组件。
 * 弹窗 + 表单 mode=view，加载数据后只读展示。
 */
export default function ViewCandidate({ candidateId, open, onClose }: Props) {
  const [candidate, setCandidate] = useState<Candidate | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !candidateId) {
      setCandidate(null);
      return;
    }
    setLoading(true);
    const req = getCandidate(candidateId);
    req.then(setCandidate)
      .catch(() => { /* 错误由 HttpClient 拦截器处理 */ })
      .finally(() => setLoading(false));
    return () => req.cancel();
  }, [open, candidateId]);

  return (
    <Modal title="查看候选人" open={open} onCancel={onClose} footer={null} width={720} destroyOnClose>
      <Spin spinning={loading}>
        {candidate && (
          <CandidateForm
            key={`view-${candidate.id}`}
            mode="view"
            candidate={candidate}
            onCancel={onClose}
          />
        )}
      </Spin>
    </Modal>
  );
}
