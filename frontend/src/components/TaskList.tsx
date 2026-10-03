import { useCallback, useEffect, useRef, useState } from 'react';
import { Table, Button, Space, Tag, Progress, message, Popconfirm, Select } from 'antd';
import type { TablePaginationConfig } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { TaskStatus } from '../types/task';
import { listTasks, triggerTask, stopTask, resumeTask, removeTask, clearMockData } from '../api/tasks';
import { IMEInput } from './IMEInput';
import { SearchOutlined } from '@ant-design/icons';

/** 已提交到服务端的查询状态：关键字 + 分页参数 */
interface TaskQueryState {
  keyword?: string;
  page: number;
  pageSize: number;
}

/** 任务运行状态筛选（前端本地过滤当前页数据） */
type StatusFilter = 'all' | 'running' | 'stopped' | 'completed' | 'pending';

/**
 * 任务列表：服务端分页 + 关键字搜索，支持触发/停止/继续/清理/删除。
 * 运行中的任务每 3 秒自动轮询刷新状态。
 */
export default function TaskList() {
  const [tasks, setTasks] = useState<TaskStatus[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<TaskQueryState>({ page: 1, pageSize: 10 });
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const pollRef = useRef<number | null>(null);
  // 轮询时读取最新查询条件（避免 interval 依赖 query 反复重建）
  const queryRef = useRef(query);
  queryRef.current = query;

  // 输入中的关键字（点击搜索后才提交到 query）
  const [keyword, setKeyword] = useState('');
  const keywordRef = useRef('');
  keywordRef.current = keyword;

  /** 加载当前查询页；返回可取消 Promise 供 effect cleanup 使用 */
  const loadTasks = useCallback(() => {
    const req = listTasks(queryRef.current);
    req.then((result) => {
      setTasks(result.records);
      setTotal(result.total);
    }).catch(() => {
      // 静默处理（可能是 abort）
    });
    return req;
  }, []);

  useEffect(() => {
    setLoading(true);
    const req = loadTasks();
    req.finally(() => setLoading(false));
    return () => req.cancel();
  }, [query, loadTasks]);

  // 有运行中任务时轮询（刷新当前查询页）
  useEffect(() => {
    const hasRunning = tasks.some((t) => t.running);
    if (hasRunning) {
      pollRef.current = window.setInterval(() => loadTasks(), 3000);
      return () => {
        if (pollRef.current) clearInterval(pollRef.current);
      };
    }
  }, [tasks, loadTasks]);

  /** 搜索：提交关键字并重置到第一页 */
  const handleSearch = () => {
    setQuery({ keyword: keywordRef.current || undefined, page: 1, pageSize: query.pageSize });
  };

  /** 翻页 / 修改每页条数 */
  const handleTableChange = (p: TablePaginationConfig) => {
    setQuery({ ...query, page: p.current ?? 1, pageSize: p.pageSize ?? 10 });
  };

  /** 刷新当前页（删除最后一条时回退一页，避免停留在空页） */
  const refresh = () => {
    const lastRowOnPage = tasks.length === 1 && query.page > 1;
    setQuery(lastRowOnPage
      ? { ...query, page: query.page - 1 }
      : { ...query });
  };

  const handleTrigger = async (taskId: string) => {
    try {
      await triggerTask(taskId);
      message.success('任务已触发');
      loadTasks();
    } catch (e) {
      message.error('触发失败');
    }
  };

  const handleStop = async (taskId: string) => {
    try {
      await stopTask(taskId);
      message.success('已请求停止');
      loadTasks();
    } catch (e) {
      message.error('停止失败');
    }
  };

  const handleResume = async (taskId: string) => {
    try {
      await resumeTask(taskId);
      message.success('已继续执行');
      loadTasks();
    } catch (e) {
      message.error('继续失败');
    }
  };

  const handleRemove = async (taskId: string) => {
    try {
      await removeTask(taskId);
      message.success('任务已删除');
      refresh();
    } catch (e) {
      message.error('删除失败');
    }
  };

  const handleClearData = async (mockTag: string) => {
    if (!mockTag) {
      message.warning('该任务无数据标志');
      return;
    }
    try {
      const result = await clearMockData(mockTag);
      message.success(`已清理 ${result.deleted} 条数据`);
    } catch (e) {
      message.error('清理失败');
    }
  };

  const columns: ColumnsType<TaskStatus> = [
    { title: '任务ID', dataIndex: 'taskId', key: 'taskId', width: 160, ellipsis: true },
    { title: '名称', dataIndex: 'name', key: 'name', width: 120 },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_, r) => {
        if (r.running) return <Tag color="processing">运行中</Tag>;
        if (r.stopRequested) return <Tag color="warning">已停止</Tag>;
        if (r.endTime) return <Tag color="success">已完成</Tag>;
        return <Tag>待触发</Tag>;
      },
    },
    {
      title: '进度',
      key: 'progress',
      width: 200,
      render: (_, r) => {
        const percent = r.total > 0 ? Math.round((r.processed / r.total) * 100) : 0;
        return (
          <div>
            <Progress percent={percent} size="small" />
            <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {r.processed.toLocaleString()} / {r.total.toLocaleString()}
            </span>
          </div>
        );
      },
    },
    {
      title: '数据标志',
      dataIndex: 'mockTag',
      key: 'mockTag',
      width: 180,
      ellipsis: true,
      render: (_, r) => r.mockTag || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_, r) => (
        <Space size="small">
          {r.running ? (
            <Button size="small" type="link" danger onClick={() => handleStop(r.taskId)}>
              停止
            </Button>
          ) : r.stopRequested ? (
            <Button size="small" type="link" onClick={() => handleResume(r.taskId)}>
              继续
            </Button>
          ) : (
            <Button size="small" type="link" onClick={() => handleTrigger(r.taskId)}>
              触发
            </Button>
          )}
          <Popconfirm
            title="确定清理该任务的模拟数据？"
            onConfirm={() => handleClearData(r.mockTag || '')}
          >
            <Button size="small" type="link">清理数据</Button>
          </Popconfirm>
          <Popconfirm
            title="确定删除该任务？"
            onConfirm={() => handleRemove(r.taskId)}
          >
            <Button size="small" type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  /** 状态筛选：本地过滤当前页 */
  const filteredTasks = statusFilter === 'all'
    ? tasks
    : tasks.filter((t) => {
        if (statusFilter === 'running') return t.running;
        if (statusFilter === 'stopped') return !t.running && t.stopRequested;
        if (statusFilter === 'completed') return !t.running && !t.stopRequested && t.endTime;
        return !t.running && !t.stopRequested && !t.endTime;
      });

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
        <IMEInput
          placeholder="搜索任务ID / 名称 / 数据标志"
          allowClear
          style={{ width: 260 }}
          onPressEnter={handleSearch}
          onDebouncedChange={setKeyword}
        />
        <Select<StatusFilter>
          style={{ width: 120 }}
          value={statusFilter}
          onChange={setStatusFilter}
          options={[
            { label: '全部状态', value: 'all' },
            { label: '运行中', value: 'running' },
            { label: '已停止', value: 'stopped' },
            { label: '已完成', value: 'completed' },
            { label: '待触发', value: 'pending' },
          ]}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
          搜索
        </Button>
      </div>
      <Table
        columns={columns}
        dataSource={filteredTasks}
        rowKey="taskId"
        loading={loading}
        size="small"
        onChange={handleTableChange}
        pagination={{
          current: query.page,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />
    </div>
  );
}
