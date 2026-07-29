export default function DashboardPage() {
  return (
    <section className="max-w-4xl">
      <div className="border-b border-neutral-200 pb-4">
        <h1 className="text-xl font-semibold text-neutral-950">工作台</h1>
        <p className="mt-1 text-sm text-neutral-600">从左侧导航进入已授权的管理功能。</p>
      </div>
      <div className="py-12 text-center">
        <p className="text-sm font-medium text-neutral-700">暂无待处理事项</p>
        <p className="mt-1 text-sm text-neutral-500">业务工作区将在后续模块中逐步开放。</p>
      </div>
    </section>
  );
}
