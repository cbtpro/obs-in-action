import { useState } from 'react';
import { Modal, Form, Input, InputNumber, Button } from 'antd';
import type { SeedTaskParams } from '../types/task';

interface TaskFormProps {
  open: boolean;
  submitting: boolean;
  onSubmit: (params: SeedTaskParams) => void;
  onCancel: () => void;
}

/**
 * 注册播种任务表单（Modal）。
 */
export default function TaskForm({ open, submitting, onSubmit, onCancel }: TaskFormProps) {
  const [form] = Form.useForm<SeedTaskParams>();
  const [mockTag, setMockTag] = useState('');

  // 自动生成数据标志
  const generateMockTag = () => {
    const now = new Date();
    const tag = `MOCK_${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`;
    setMockTag(tag);
    form.setFieldValue('mockTag', tag);
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    // 如果 mockTag 为空，自动生成
    if (!values.mockTag) {
      const now = new Date();
      values.mockTag = `MOCK_${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`;
    }
    onSubmit(values);
  };

  return (
    <Modal
      title="注册播种任务"
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={submitting}
      okText="注册"
      cancelText="取消"
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ total: 100000, batchSize: 10000, intervalMillis: 0 }}
      >
        <Form.Item label="任务名称" name="name">
          <Input placeholder="数据播种" />
        </Form.Item>

        <Form.Item
          label="数据总量"
          name="total"
          rules={[{ required: true, message: '请输入数据总量' }]}
        >
          <InputNumber min={1} style={{ width: '100%' }} placeholder="100000" />
        </Form.Item>

        <Form.Item
          label="批量大小"
          name="batchSize"
          rules={[{ required: true, message: '请输入批量大小' }]}
        >
          <InputNumber min={1} style={{ width: '100%' }} placeholder="10000" />
        </Form.Item>

        <Form.Item label="批间间隔（毫秒）" name="intervalMillis">
          <InputNumber min={0} style={{ width: '100%' }} placeholder="0" />
        </Form.Item>

        <Form.Item label="Cron 表达式（可选）" name="cronExpression">
          <Input placeholder="0 0 2 * * ?（留空则仅手动触发）" />
        </Form.Item>

        <Form.Item label="数据标志" name="mockTag">
          <Input
            placeholder="自动生成"
            value={mockTag}
            onChange={(e) => setMockTag(e.target.value)}
            addonAfter={<Button size="small" onClick={generateMockTag}>生成</Button>}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
