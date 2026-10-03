import { Routes, Route } from 'react-router-dom';
import { appRoutes, navRoutes } from './routeConfig';
import RequirePermission from './RequirePermission';

interface KeepAliveContentProps {
  /** 已打开的 Tab key 列表 */
  openTabs: string[];
  /** 当前激活的 Tab key，非菜单路由时为 undefined */
  activeKey: string | undefined;
}

/**
 * 路由内容渲染器，支持 keepalive。
 *
 * 菜单路由：所有已打开的页面同时挂载，通过 display 控制显隐，
 * 切换 Tab 时不卸载组件，保留页面状态（列表数据、滚动位置、表单输入等）。
 *
 * 非菜单路由（403/404）：不 keepalive，仅渲染当前匹配项。
 */
export default function KeepAliveContent({ openTabs, activeKey }: KeepAliveContentProps) {
  const isNavPage = activeKey !== undefined;

  // 非菜单路由（403/404）不 keepalive
  if (!isNavPage) {
    return (
      <Routes>
        {appRoutes
          .filter((r) => !r.showInMenu)
          .map((route) => (
            <Route
              key={route.key}
              path={route.path}
              element={
                <RequirePermission permission={route.permission}>
                  {route.element}
                </RequirePermission>
              }
            />
          ))}
      </Routes>
    );
  }

  // 菜单路由：keepalive，已打开的页面同时渲染，仅激活项可见
  return (
    <>
      {openTabs.map((key) => {
        const route = navRoutes.find((r) => r.key === key);
        if (!route) return null;
        return (
          <div key={key} style={{ display: key === activeKey ? 'block' : 'none' }}>
            <RequirePermission permission={route.permission}>
              {route.element}
            </RequirePermission>
          </div>
        );
      })}
    </>
  );
}
