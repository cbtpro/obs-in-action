import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Popconfirm,
  message,
  Image,
  Tag,
  Select,
  theme,
  Flex,
} from 'antd';
import type { TablePaginationConfig } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { listCandidates, deleteCandidate } from '../api/candidates';
import { Candidate } from '../types/candidate';
import { IMEInput } from './IMEInput';
import AddCandidate from './AddCandidate';
import EditCandidate from './EditCandidate';
import ViewCandidate from './ViewCandidate';

/** 已提交到服务端的查询状态：过滤条件 + 分页参数 */
interface CandidateQueryState {
  name?: string;
  idCard?: string;
  gender?: string;
  page: number;
  pageSize: number;
}

/**
 * 候选人列表页：服务端分页表格 + 搜索 + 新增/查看/编辑/删除。
 * query 状态变化（搜索/翻页/改每页条数）即触发重新请求。
 */
export default function CandidateList() {
  const { token } = theme.useToken();
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<CandidateQueryState>({ page: 1, pageSize: 10 });

  // 输入中的筛选条件（点击搜索后才提交到 query）
  const [name, setName] = useState('');
  const [idCard, setIdCard] = useState('');
  const [gender, setGender] = useState<string | undefined>(undefined);

  const [addOpen, setAddOpen] = useState(false);
  const [viewId, setViewId] = useState<number | null>(null);
  const [editId, setEditId] = useState<number | null>(null);

  const load = useCallback((q: CandidateQueryState) => {
    setLoading(true);
    const req = listCandidates(q);
    req.then(data => {
      setCandidates(data.records);
      setTotal(data.total);
    })
      .catch(() => { /* 错误由 HttpClient 拦截器处理 */ })
      .finally(() => setLoading(false));
    return req;
  }, []);

  useEffect(() => {
    const req = load(query);
    return () => req.cancel();
  }, [query, load]);

  /** IME 输入防抖更新状态 */
  const handleNameChange = (value: string) => setName(value);
  const handleIdCardChange = (value: string) => setIdCard(value);

  /** 点击搜索按钮或回车：提交筛选条件并重置到第一页 */
  const handleSearch = () => {
    setQuery({ name, idCard, gender, page: 1, pageSize: query.pageSize });
  };

  /** 翻页 / 修改每页条数 */
  const handleTableChange = (p: TablePaginationConfig) => {
    setQuery({ ...query, page: p.current ?? 1, pageSize: p.pageSize ?? 10 });
  };

  /** 刷新当前页（删除最后一条时回退一页，避免停留在空页） */
  const refresh = () => {
    const lastRowOnPage = candidates.length === 1 && query.page > 1;
    setQuery(lastRowOnPage
      ? { ...query, page: query.page - 1 }
      : { ...query });
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteCandidate(id);
      message.success('删除成功');
      refresh();
    } catch {
      // 错误由拦截器提示
    }
  };

  const columns: ColumnsType<Candidate> = [
    {
      title: '照片',
      dataIndex: 'photoUrl',
      width: 80,
      render: (url: string | null) =>
        url ? (
          <Image
            src={url}
            alt="照片"
            width={48}
            height={48}
            style={{ objectFit: 'cover', borderRadius: 0 }}
          />
        ) : (
          <span style={{ color: token.colorTextTertiary }}>无</span>
        ),
    },
    { title: '姓名', dataIndex: 'name', width: 100 },
    { title: '身份证号', dataIndex: 'idCard', width: 200 },
    {
      title: '性别',
      dataIndex: 'gender',
      width: 70,
      render: (g: string | null) =>
        g ? <Tag color={g === '男' ? 'blue' : 'pink'}>{g}</Tag> : '-',
    },
    { title: '年龄', dataIndex: 'age', width: 70 },
    {
      title: '简历',
      dataIndex: 'resumeUrl',
      width: 80,
      render: (url: string | null) =>
        url ? (
          <a href={url} target="_blank" rel="noreferrer">
            下载
          </a>
        ) : (
          <span style={{ color: token.colorTextTertiary }}>无</span>
        ),
    },
    { title: '个人简介', dataIndex: 'bio', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width:  280,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => setViewId(record.id)}
          >
            查看
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => setEditId(record.id)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定删除该候选人？"
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <span>姓名</span>
          <IMEInput
            placeholder="请输入姓名"
            allowClear
            style={{ width: 160 }}
            onPressEnter={handleSearch}
            onDebouncedChange={handleNameChange}
          />
          <span>身份证号</span>
          <IMEInput
            placeholder="请输入身份证号"
            allowClear
            style={{ width: 200 }}
            onPressEnter={handleSearch}
            onDebouncedChange={handleIdCardChange}
          />
          <span>性别</span>
          <Select
            placeholder="请选择"
            allowClear
            style={{ width: 100 }}
            options={[
              { label: '男', value: '男' },
              { label: '女', value: '女' },
            ]}
            onChange={(val) => setGender(val)}
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            搜索
          </Button>
        </Space>
      </div>
      <Flex gap="medium" vertical>
        <Flex align="center" gap="medium">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddOpen(true)}>
            新增候选人
          </Button>
        </Flex>
      </Flex>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={candidates}
        loading={loading}
        onChange={handleTableChange}
        pagination={{
          current: query.page,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />
      <AddCandidate
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onSuccess={() => {
          setAddOpen(false);
          // 新记录按 id 倒序排在最前，跳回第一页可见
          setQuery({ ...query, page: 1 });
        }}
      />
      <ViewCandidate
        candidateId={viewId}
        open={viewId !== null}
        onClose={() => setViewId(null)}
      />
      <EditCandidate
        candidateId={editId}
        open={editId !== null}
        onClose={() => setEditId(null)}
        onSuccess={() => {
          setEditId(null);
          setQuery({ ...query });
        }}
      />
    </div>
  );
}
