import { ChevronLeft, ChevronRight, History } from 'lucide-react';
import type { FlightStatusLog } from '../types/flight';
import type { AnalyticsExecution, AnalyticsPage } from '../types/analytics';

interface Props {
  page: AnalyticsPage<AnalyticsExecution>;
  selected: { flightIata: string; flightDate: string } | null;
  history: FlightStatusLog[];
  historyLoading: boolean;
  historyError: string | null;
  onSelect: (execution: AnalyticsExecution) => void;
  onPageChange: (page: number) => void;
}

const names: Record<string, string> = {
  LANDED: 'Приземлился', CANCELLED: 'Отменён', DELAYED: 'Задержан',
  SCHEDULED: 'Запланирован', ACTIVE: 'В полёте', ON_TIME: 'По расписанию',
};
export const statusName = (status: string | null): string => status ? names[status.toUpperCase()] ?? status : '—';
function localDate(date: string): string {
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  return parts ? `${parts[3]}.${parts[2]}.${parts[1]}` : date;
}
function syncTime(iso: string | null): string {
  if (!iso) return 'Нет данных';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return 'Нет данных';
  return new Intl.DateTimeFormat('ru-RU', { timeZone: 'Europe/Moscow', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date) + ' MSK';
}
function delay(value: number | null): string { return value === null ? 'Нет данных' : `${value} мин`; }

export default function FlightExecutionsTable({ page, selected, history, historyLoading, historyError, onSelect, onPageChange }: Props) {
  const hasRecords = page.content.length > 0;
  return (
    <section aria-labelledby="executions-heading" className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 p-5 sm:px-6">
        <h2 id="executions-heading" className="text-lg font-extrabold text-slate-900">Выполнения рейсов</h2>
        <span className="text-sm text-slate-500">Записей: {page.totalElements}</span>
      </div>
      {!hasRecords ? <p className="px-6 py-10 text-center text-sm text-slate-500">В выбранном периоде нет сохранённых выполнений.</p> : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[780px] text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr>
              <th className="px-5 py-3">Дата</th><th className="px-5 py-3">Рейс</th><th className="px-5 py-3">Маршрут</th>
              <th className="px-5 py-3">Итоговый статус</th><th className="px-5 py-3">Задержка прибытия</th><th className="px-5 py-3">Последняя синхронизация</th><th className="px-5 py-3">История</th>
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {page.content.map((item, index) => {
                const expanded = selected?.flightIata === item.flightIata && selected.flightDate === item.flightDate;
                return (
                  <tr key={`${item.flightIata}-${item.flightDate}-${index}`} className="hover:bg-blue-50/30">
                    <td className="whitespace-nowrap px-5 py-4 font-mono text-slate-700">{localDate(item.flightDate)}</td>
                    <td className="px-5 py-4 font-bold text-slate-900">{item.flightIata}</td>
                    <td className="whitespace-nowrap px-5 py-4 text-slate-700">{item.departureIata || '—'} → {item.arrivalIata || '—'}</td>
                    <td className={`px-5 py-4 font-semibold ${item.status === 'CANCELLED' ? 'text-red-600' : 'text-slate-700'}`}>{statusName(item.status)}</td>
                    <td className="whitespace-nowrap px-5 py-4 text-slate-700">{delay(item.arrivalDelayMinutes)}</td>
                    <td className="whitespace-nowrap px-5 py-4 text-slate-500">{syncTime(item.lastSyncedAt)}</td>
                    <td className="px-5 py-4"><button type="button" aria-expanded={expanded} onClick={() => onSelect(item)} aria-label={`История ${item.flightIata} ${item.flightDate}`} className="inline-flex items-center gap-1.5 rounded-lg bg-blue-50 px-3 py-2 font-bold text-blue-700 hover:bg-blue-100"><History className="h-4 w-4" aria-hidden="true" /> История</button></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {selected && (
        <div className="border-t border-blue-100 bg-blue-50/40 px-5 py-5 sm:px-6" aria-live="polite">
          <h3 className="mb-3 font-extrabold text-slate-900">История {selected.flightIata} за {localDate(selected.flightDate)}</h3>
          {historyLoading ? <p className="text-sm text-slate-500">Загружаем историю…</p> : historyError ? <p role="alert" className="text-sm text-red-700">{historyError}</p> : history.length === 0 ? <p className="text-sm text-slate-500">История изменений отсутствует.</p> : (
            <ol className="space-y-2">
              {history.map((log, index) => <li key={`${log.recordedAt}-${index}`} className="rounded-xl border border-slate-100 bg-white p-3 text-sm text-slate-700">
                <span className="font-mono text-xs text-slate-500">{syncTime(log.recordedAt)}</span>
                <span className="ml-3 font-semibold">{statusName(log.previousStatus)} → {statusName(log.newStatus)}</span>
                <span className="ml-3 text-slate-500">Задержка в событии: {typeof log.delayMinutes === 'number' ? `${log.delayMinutes} мин` : 'Нет данных'}</span>
              </li>)}
            </ol>
          )}
        </div>
      )}
      <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-5 py-4 text-sm sm:px-6">
        <span className="text-slate-500">Страница {page.totalPages === 0 ? 0 : page.number + 1} из {page.totalPages}</span>
        <div className="flex items-center gap-2">
          <button type="button" disabled={page.number === 0} onClick={() => onPageChange(page.number - 1)} className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-2 font-semibold text-slate-700 disabled:cursor-not-allowed disabled:opacity-40"><ChevronLeft className="h-4 w-4" aria-hidden="true" /> Назад</button>
          <button type="button" disabled={page.totalPages === 0 || page.number >= page.totalPages - 1} onClick={() => onPageChange(page.number + 1)} className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-2 font-semibold text-slate-700 disabled:cursor-not-allowed disabled:opacity-40">Далее <ChevronRight className="h-4 w-4" aria-hidden="true" /></button>
        </div>
      </div>
    </section>
  );
}
