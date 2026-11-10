import { useEffect, useRef, useState } from 'react';
import { BarChart3, Plane } from 'lucide-react';
import AnalyticsFilters from '../components/AnalyticsFilters';
import SiteNavigation from '../components/SiteNavigation';
import ReliabilityCards from '../components/ReliabilityCards';
import FlightExecutionsTable from '../components/FlightExecutionsTable';
import { getExecutions, getExecutionHistory, getFlightAnalytics, getRouteAnalytics } from '../services/analyticsApi';
import type { AnalyticsExecution, AnalyticsFilter, AnalyticsPage as ExecutionsPage, AnalyticsSummary } from '../types/analytics';
import type { FlightStatusLog } from '../types/flight';

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : 'Неизвестная ошибка получения данных.';
}

export default function AnalyticsPage() {
  const [filter, setFilter] = useState<AnalyticsFilter | null>(null);
  const [searchRevision, setSearchRevision] = useState(0);
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [executionsPage, setExecutionsPage] = useState<ExecutionsPage<AnalyticsExecution> | null>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [executionsLoading, setExecutionsLoading] = useState(false);
  const [executionsError, setExecutionsError] = useState<string | null>(null);
  const [selected, setSelected] = useState<{ flightIata: string; flightDate: string } | null>(null);
  const [history, setHistory] = useState<FlightStatusLog[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const historyRequest = useRef(0);

  const submit = (next: AnalyticsFilter) => {
    setFilter(next);
    setSearchRevision((value) => value + 1);
    setPageIndex(0);
    setSummary(null);
    setSummaryError(null);
    setExecutionsPage(null);
    setExecutionsError(null);
    setSelected(null);
    setHistory([]);
    setHistoryError(null);
    setHistoryLoading(false);
  };

  useEffect(() => {
    if (!filter) return;
    const controller = new AbortController();
    setSummaryLoading(true);
    setSummaryError(null);
    const request = filter.type === 'FLIGHT'
      ? getFlightAnalytics(filter.flightIata, filter.from, filter.to, controller.signal)
      : getRouteAnalytics(filter.departure, filter.arrival, filter.from, filter.to, controller.signal);
    request.then((result) => {
      if (!controller.signal.aborted) setSummary(result);
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) setSummaryError(errorText(error));
    }).finally(() => {
      if (!controller.signal.aborted) setSummaryLoading(false);
    });
    return () => controller.abort();
  }, [filter, searchRevision]);

  useEffect(() => {
    if (!filter) return;
    const controller = new AbortController();
    setExecutionsLoading(true);
    setExecutionsError(null);
    setExecutionsPage(null);
    getExecutions(filter, pageIndex, 20, controller.signal).then((result) => {
      if (!controller.signal.aborted) setExecutionsPage(result);
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) setExecutionsError(errorText(error));
    }).finally(() => {
      if (!controller.signal.aborted) setExecutionsLoading(false);
    });
    return () => controller.abort();
  }, [filter, pageIndex, searchRevision]);

  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    const requestNumber = ++historyRequest.current;
    setHistoryLoading(true);
    setHistoryError(null);
    setHistory([]);
    getExecutionHistory(selected.flightIata, selected.flightDate, controller.signal).then((data) => {
      if (!controller.signal.aborted && historyRequest.current === requestNumber) setHistory(data);
    }).catch((error: unknown) => {
      if (!controller.signal.aborted && historyRequest.current === requestNumber) setHistoryError(errorText(error));
    }).finally(() => {
      if (!controller.signal.aborted && historyRequest.current === requestNumber) setHistoryLoading(false);
    });
    return () => controller.abort();
  }, [selected]);

  const selectExecution = (item: AnalyticsExecution) => {
    if (selected?.flightIata === item.flightIata && selected.flightDate === item.flightDate) {
      setSelected(null);
      setHistory([]);
      setHistoryLoading(false);
      return;
    }
    setSelected({ flightIata: item.flightIata, flightDate: item.flightDate });
  };

  const changePage = (next: number) => {
    setPageIndex(next);
    setSelected(null);
    setHistory([]);
    setHistoryError(null);
    setHistoryLoading(false);
  };

  return (
    <div className="flex min-h-screen flex-col bg-[#f7f9fc] text-slate-900 bg-aviation-grid">
      <header className="border-b border-slate-200 bg-white/95 shadow-sm">
        <div className="h-1 bg-gradient-to-r from-blue-400 via-blue-600 to-indigo-500" />
        <div className="mx-auto flex max-w-5xl items-center gap-3 px-4 py-4 sm:px-6">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-600 text-white"><Plane className="h-6 w-6" aria-hidden="true" /></div>
          <div><a href="/" className="block text-lg font-extrabold text-slate-900">Flight Tracker</a><span className="text-xs text-slate-400">Мониторинг перелётов</span></div>
        </div>
      </header>
      <SiteNavigation />
      <main className="mx-auto w-full max-w-5xl flex-1 space-y-6 px-4 py-6 sm:px-6">
        <div className="flex items-center gap-3"><div className="rounded-2xl bg-blue-50 p-3 text-blue-600"><BarChart3 className="h-6 w-6" aria-hidden="true" /></div><div><h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">Аналитика надёжности рейсов</h1><p className="mt-1 text-sm text-slate-500">История и показатели на основе данных PostgreSQL</p></div></div>
        <AnalyticsFilters onSubmit={submit} />
        {!filter && <div className="rounded-2xl border border-dashed border-blue-200 bg-white/80 p-10 text-center text-sm text-slate-500">Укажите параметры и нажмите «Анализировать», чтобы получить статистику.</div>}
        {filter && (
          <>
            {summaryLoading && <p role="status" className="rounded-2xl bg-white p-5 text-sm text-slate-500">Загружаем показатели…</p>}
            {summaryError && <p role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">Показатели: {summaryError}</p>}
            {summary && <ReliabilityCards summary={summary} />}
            {executionsLoading && <p role="status" className="rounded-2xl bg-white p-5 text-sm text-slate-500">Загружаем список выполнений…</p>}
            {executionsError && <p role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">Список выполнений: {executionsError}</p>}
            {executionsPage && <FlightExecutionsTable page={executionsPage} selected={selected} history={history} historyLoading={historyLoading} historyError={historyError} onSelect={selectExecution} onPageChange={changePage} />}
          </>
        )}
      </main>
      <footer className="border-t border-slate-200 bg-white px-6 py-5 text-center text-xs text-slate-400">Flight Tracker · Аналитика сохранённых данных · Время синхронизации: MSK (UTC+3)</footer>
    </div>
  );
}
