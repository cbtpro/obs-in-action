import { useState } from 'react';
import { Modal, message } from 'antd';
import CandidateForm from './CandidateForm';
import { createCandidate } from '../api/candidates';
import { CandidateFormValues } from '../types/candidate';

interface Props {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * 新增候选人组件。
 * 弹窗 + 表单 mode=add，提交时调用 createCandidate。
 */
export default function AddCandidate({ open, onClose, onSuccess }: Props) {
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (values: CandidateFormValues) => {
    setSubmitting(true);
    try {
      await createCandidate(values);
      message.success('新增成功');
      onSuccess();
    } catch {
      // 错误由拦截器提示
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="新增候选人" open={open} onCancel={onClose} footer={null} width={720} destroyOnClose>
      <CandidateForm
        key="add"
        mode="add"
        onSubmit={handleSubmit}
        onCancel={onClose}
        submitting={submitting}
      />
    </Modal>
  );
}
