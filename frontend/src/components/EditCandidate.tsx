import { useEffect, useState } from 'react';
import { Modal, Spin, message } from 'antd';
import CandidateForm from './CandidateForm';
import { getCandidate, updateCandidate } from '../api/candidates';
import { Candidate, CandidateFormValues } from '../types/candidate';

interface Props {
  candidateId: number | null;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * 编辑候选人组件。
 * 弹窗 + 表单 mode=edit，加载数据后可编辑，提交时调用 updateCandidate。
 */
export default function EditCandidate({ candidateId, open, onClose, onSuccess }: Props) {
  const [candidate, setCandidate] = useState<Candidate | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

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

  const handleSubmit = async (values: CandidateFormValues) => {
    if (!candidate) return;
    setSubmitting(true);
    try {
      await updateCandidate(candidate.id, values);
      message.success('修改成功');
      onSuccess();
    } catch {
      // 错误由拦截器提示
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="编辑候选人" open={open} onCancel={onClose} footer={null} width={720} destroyOnClose>
      <Spin spinning={loading}>
        {candidate && (
          <CandidateForm
            key={`edit-${candidate.id}`}
            mode="edit"
            candidate={candidate}
            onSubmit={handleSubmit}
            onCancel={onClose}
            submitting={submitting}
          />
        )}
      </Spin>
    </Modal>
  );
}
