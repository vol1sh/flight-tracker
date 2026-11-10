import { AlertTriangle, CheckCircle2, Clock3, Database, PlaneLanding, Ban, ShieldCheck } from 'lucide-react';
import type { AnalyticsSummary } from '../types/analytics';

interface Props { summary: AnalyticsSummary }

const decimal = (value: number): string => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value);
const percentage = (value: number | null): string => value === null ? 'Нет данных' : `${decimal(value * 100)}%`;
const minutes = (value: number | null): string => value === null ? 'Нет данных' : `${decimal(value)} мин`;

function formatSync(iso: string | null): string {
  if (!iso) return 'Нет данных';
  const instant = new Date(iso);
  if (Number.isNaN(instant.getTime())) return 'Нет данных';
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'Europe/Moscow', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(instant) + ' MSK (UTC+3)';
}

export default function ReliabilityCards({ summary }: Props) {
  const sufficient = summary.dataSufficient;
  const metrics = [
    { name: 'Средняя задержка прибытия', value: minutes(sufficient ? summary.averageDelayMinutes : null), Icon: Clock3, accent: 'text-blue-600 bg-blue-50' },
    { name: 'Без задержки по прибытии', value: percentage(sufficient ? summary.onTimeRate : null), Icon: CheckCircle2, accent: 'text-emerald-600 bg-emerald-50' },
    { name: 'Доля отмен', value: percentage(sufficient ? summary.cancellationRate : null), Icon: Ban, accent: 'text-rose-600 bg-rose-50' },
    { name: 'Рейтинг надёжности', value: !sufficient || summary.reliabilityScore === null ? 'Нет данных' : `${decimal(summary.reliabilityScore)} / 100`, Icon: ShieldCheck, accent: 'text-indigo-600 bg-indigo-50' },
  ];

  return (
    <section aria-label="Показатели надёжности" className="space-y-4">
      {!summary.dataSufficient && (
        <div role="alert" className="flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <div><strong className="block font-bold">Недостаточно данных для достоверного анализа</strong><span>{summary.message || 'Собранных завершённых рейсов и известных задержек недостаточно.'}</span></div>
        </div>
      )}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {metrics.map(({ name, value, Icon, accent }) => (
          <div key={name} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className={`mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl ${accent}`}><Icon className="h-5 w-5" aria-hidden="true" /></div>
            <p className="text-sm leading-snug text-slate-500">{name}</p>
            <p className="mt-1 text-xl font-extrabold text-slate-900" aria-label={`${name}: ${value}`}>{value}</p>
          </div>
        ))}
      </div>
      <div className="rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <div className="mb-3 flex items-center gap-2 text-sm font-bold text-slate-900"><PlaneLanding className="h-4 w-4 text-blue-500" aria-hidden="true" /> Охват сохранённых данных</div>
        <dl className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
          <div><dt className="text-slate-500">Всего выполнений</dt><dd className="mt-1 font-bold text-slate-900">{summary.sampleSize}</dd></div>
          <div><dt className="text-slate-500">Приземлилось</dt><dd className="mt-1 font-bold text-slate-900">{summary.landedCount}</dd></div>
          <div><dt className="text-slate-500">Отменено</dt><dd className="mt-1 font-bold text-slate-900">{summary.cancelledCount}</dd></div>
          <div><dt className="text-slate-500">Известна задержка прибытия</dt><dd className="mt-1 font-bold text-slate-900">{summary.knownArrivalDelayCount}</dd></div>
        </dl>
        <div className="mt-4 flex flex-wrap gap-x-6 gap-y-2 border-t border-slate-100 pt-4 text-xs text-slate-500">
          <span className="inline-flex items-center gap-1.5"><Database className="h-4 w-4" aria-hidden="true" /> Источник: <strong>{summary.dataSource}</strong></span>
          <span>Последняя успешная синхронизация: <strong>{formatSync(summary.lastUpdatedAt)}</strong></span>
        </div>
        <p className="mt-3 text-xs leading-relaxed text-amber-700">В статистику входят только рейсы, сохранённые в PostgreSQL. Охват может быть неполным и не отражать все рейсы маршрута.</p>
      </div>
    </section>
  );
}
