import { UserOutlined, UploadOutlined, ScheduleOutlined } from '@ant-design/icons';
import CandidatePage from '../pages/CandidatePage';
import FilePage from '../pages/FilePage';
import TaskPage from '../pages/TaskPage';
import Forbidden from '../pages/Forbidden';
import NotFound from '../pages/NotFound';

/**
 * 应用路由配置。
 *
 * 每个路由项可携带 permission 标识，RequirePermission 组件会据此决定是否放行。
 * 新增路由只需在此数组追加一项，无需改动 App.tsx 路由渲染逻辑（开闭原则）。
 */
export interface AppRoute {
  /** 路由唯一标识，同时用于菜单选中状态和 Tab key */
  key: string;
  /** 路由路径 */
  path: string;
  /** 菜单 / Tab 显示名称 */
  label: string;
  /** 菜单图标，showInMenu 为 false 时可为 null */
  icon: React.ReactNode;
  /** 路由渲染组件 */
  element: React.ReactNode;
  /** 权限标识，后续接入权限系统时由 RequirePermission 校验 */
  permission?: string;
  /** 是否在侧栏菜单中显示 */
  showInMenu: boolean;
}

export const appRoutes: AppRoute[] = [
  {
    key: 'candidates',
    path: '/candidates',
    label: '候选人管理',
    icon: <UserOutlined />,
    element: <CandidatePage />,
    showInMenu: true,
  },
  {
    key: 'files',
    path: '/files',
    label: '文件管理',
    icon: <UploadOutlined />,
    element: <FilePage />,
    showInMenu: true,
  },
  {
    key: 'tasks',
    path: '/tasks',
    label: '任务管理',
    icon: <ScheduleOutlined />,
    element: <TaskPage />,
    showInMenu: true,
  },
  {
    key: 'forbidden',
    path: '/403',
    label: '无权限',
    icon: null,
    element: <Forbidden />,
    showInMenu: false,
  },
  {
    key: 'not-found',
    path: '*',
    label: '页面不存在',
    icon: null,
    element: <NotFound />,
    showInMenu: false,
  },
];

/** 侧栏菜单项（仅 showInMenu 为 true 的路由） */
export const navRoutes = appRoutes.filter((r) => r.showInMenu);
