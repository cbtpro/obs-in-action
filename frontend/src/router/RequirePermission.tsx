import type { ReactNode } from 'react';

/**
 * 路由权限守卫。
 *
 * 当前为直通模式，所有路由均放行。
 * 后续接入权限系统时，在此组件内校验 permission 标识，
 * 无权限时重定向到 /403，无需改动路由配置或 App.tsx。
 */
interface RequirePermissionProps {
  /** 子节点（受保护的路由组件） */
  children: ReactNode;
  /** 权限标识，与 routeConfig 中的 permission 字段对应 */
  permission?: string;
}

export default function RequirePermission({ children }: RequirePermissionProps) {
  // TODO: 接入权限系统后实现校验逻辑
  // const { hasPermission } = useAuth();
  // if (permission && !hasPermission(permission)) {
  //   return <Navigate to="/403" replace />;
  // }
  return <>{children}</>;
}
