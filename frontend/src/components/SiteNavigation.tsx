import { BarChart3, Search } from 'lucide-react';

export default function SiteNavigation() {
  const analytics = window.location.pathname.replace(/\/$/, '') === '/analytics';
  const linkClass = 'inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-bold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-600';
  return (
    <nav aria-label="Основные разделы" className="mx-auto w-full max-w-5xl px-4 pt-5 sm:px-6">
      <div className="inline-flex flex-wrap gap-1 rounded-2xl border border-slate-200 bg-white p-1.5 shadow-sm">
        <a aria-current={!analytics ? 'page' : undefined} href="/" className={`${linkClass} ${analytics ? 'text-slate-600 hover:bg-slate-50' : 'bg-blue-600 text-white shadow-sm'}`}>
          <Search className="h-4 w-4" aria-hidden="true" /> Поиск рейса
        </a>
        <a aria-current={analytics ? 'page' : undefined} href="/analytics" className={`${linkClass} ${analytics ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-50'}`}>
          <BarChart3 className="h-4 w-4" aria-hidden="true" /> Аналитика
        </a>
      </div>
    </nav>
  );
}
