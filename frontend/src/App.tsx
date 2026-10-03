import { useEffect, useState } from 'react';
import { ConfigProvider, Layout, Typography, Menu, Tabs, Button, Dropdown, Avatar, Space, theme } from 'antd';
import type { MenuProps } from 'antd';
import {
  UserOutlined,
  SunOutlined,
  MoonOutlined,
  SettingOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import zhCN from 'antd/locale/zh_CN';
import { navRoutes } from './router/routeConfig';
import KeepAliveContent from './router/KeepAliveContent';
import './styles/index.css';

const { Header, Sider, Content, Footer } = Layout;
const { Title } = Typography;

export default function App() {
  const [isDark, setIsDark] = useState(false);
  const [siderCollapsed, setSiderCollapsed] = useState(false);
  const [openTabs, setOpenTabs] = useState<string[]>(['candidates']);

  const location = useLocation();
  const navigate = useNavigate();
  const { token } = theme.useToken();

  /** 同步 data-theme 到 <html>，驱动 CSS 变量暗黑适配 */
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
  }, [isDark]);

  /** 根路径重定向到候选人管理 */
  useEffect(() => {
    if (location.pathname === '/') {
      navigate('/candidates', { replace: true });
    }
  }, [location.pathname, navigate]);

  /** 当前路由匹配的导航项 key，未匹配则为 undefined（403/404） */
  const activeKey = navRoutes.find((item) => location.pathname.startsWith(item.path))?.key;
  const isNavPage = activeKey !== undefined;

  /** 同步确保 activeKey 在 openTabs 中（首次直接访问某路由时需要） */
  const effectiveTabs = activeKey && !openTabs.includes(activeKey)
    ? [...openTabs, activeKey]
    : openTabs;

  /** 路由变更时自动打开对应 Tab */
  useEffect(() => {
    if (activeKey) {
      setOpenTabs((prev) => (prev.includes(activeKey) ? prev : [...prev, activeKey]));
    }
  }, [activeKey]);

  const handleTabClick = (key: string) => {
    const item = navRoutes.find((n) => n.key === key);
    if (item) navigate(item.path);
  };

  const handleTabClose = (targetKey: string) => {
    const newTabs = openTabs.filter((k) => k !== targetKey);
    if (newTabs.length === 0) return;
    setOpenTabs(newTabs);
    if (targetKey === activeKey) {
      const next = newTabs[newTabs.length - 1];
      const item = navRoutes.find((n) => n.key === next);
      if (item) navigate(item.path);
    }
  };

  const userMenuItems: MenuProps['items'] = [
    { key: 'settings', icon: <SettingOutlined />, label: '个人设置' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
  ];

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          borderRadius: 0,
        },
        algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
      }}
    >
      <Layout className="app-layout" style={{ minHeight: '100vh', background: token.colorBgLayout }}>
        {/* 顶部 Banner */}
        <Header
          className="app-header"
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <UserOutlined style={{ color: '#fff', fontSize: 20 }} />
            <Title level={4} style={{ color: '#fff', margin: 0 }}>
              OBS 面试管理
            </Title>
          </div>
          <Space size="middle">
            <Button
              type="text"
              icon={isDark ? <SunOutlined /> : <MoonOutlined />}
              onClick={() => setIsDark((v) => !v)}
              style={{ color: '#fff' }}
            />
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer', color: '#fff' }}>
                <Avatar icon={<UserOutlined />} size="small" />
                <span>管理员</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Layout style={{ background: token.colorBgLayout }}>
          {/* 左侧菜单 */}
          <Sider
            collapsible
            collapsed={siderCollapsed}
            onCollapse={setSiderCollapsed}
            theme={isDark ? 'dark' : 'light'}
            width={200}
            style={{ background: isDark ? undefined : token.colorBgContainer }}
          >
            <Menu
              mode="vertical"
              theme={isDark ? 'dark' : 'light'}
              selectedKeys={activeKey ? [activeKey] : []}
              onClick={({ key }) => handleTabClick(key)}
              items={navRoutes.map((r) => ({ key: r.key, icon: r.icon, label: r.label }))}
            />
          </Sider>

          {/* 右侧内容区 */}
          <Layout style={{ background: token.colorBgLayout }}>
            {/* Tab 标签栏 */}
            {isNavPage && (
              <Tabs
                type="editable-card"
                hideAdd
                activeKey={activeKey}
                onChange={handleTabClick}
                onEdit={(key, action) => action === 'remove' && handleTabClose(key as string)}
                items={openTabs.map((key) => {
                  const item = navRoutes.find((n) => n.key === key)!;
                  return { key: item.key, label: item.label, children: null };
                })}
                className="app-tabs"
                tabBarStyle={{ margin: 0, padding: '0 16px' }}
                style={{ background: token.colorBgContainer }}
              />
            )}

            {/* 页面内容（keepalive：切换 Tab 不卸载组件） */}
            <Content className="app-content">
              <KeepAliveContent openTabs={effectiveTabs} activeKey={activeKey} />
            </Content>

            {/* 底部版权 */}
            <Footer className="app-footer">
              OBS 面试管理 ©2026
            </Footer>
          </Layout>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
