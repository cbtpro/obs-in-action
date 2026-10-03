import CandidateList from '../components/CandidateList';

/**
 * 候选人管理页面。
 * 作为路由入口，封装 CandidateList 组件。
 * 后续可在此层添加页面级逻辑（如权限校验后的数据预加载）。
 */
export default function CandidatePage() {
  return <CandidateList />;
}
