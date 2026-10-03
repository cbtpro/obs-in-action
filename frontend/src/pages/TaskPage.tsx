import { useState } from 'react';
import { Button, Space, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import TaskList from '../components/TaskList';
import TaskForm from '../components/TaskForm';
import { registerSeedTask } from '../api/tasks';
import type { SeedTaskParams } from '../types/task';

/**
 * 任务管理页面：注册播种任务 + 任务列表。
 */
export default function TaskPage() {
  const [formOpen, setFormOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (params: SeedTaskParams) => {
    setSubmitting(true);
    try {
      const status = await registerSeedTask(params);
      message.success(`任务已注册: ${status.taskId}`);
      setFormOpen(false);
      // 刷新列表由 TaskList 内部轮询处理
      setTimeout(() => window.location.reload(), 500);
    } catch (e) {
      message.error('注册失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setFormOpen(true)}
        >
          注册任务
        </Button>
      </Space>
      <TaskList />
      <TaskForm
        open={formOpen}
        submitting={submitting}
        onSubmit={handleSubmit}
        onCancel={() => setFormOpen(false)}
      />
    </div>
  );
}
