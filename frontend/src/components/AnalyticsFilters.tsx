import { useState } from 'react';
import type { FormEvent } from 'react';
import { CalendarDays, Plane, Search } from 'lucide-react';
import type { AnalyticsFilter } from '../types/analytics';

interface Props {
  onSubmit: (filters: AnalyticsFilter) => void;
}

function localDay(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

const fieldClass = 'w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-semibold text-slate-900 outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100';
const labelClass = 'mb-2 block text-xs font-bold uppercase tracking-wide text-slate-500';

export default function AnalyticsFilters({ onSubmit }: Props) {
  const today = localDay(new Date());
  const monthAgo = new Date();
  monthAgo.setDate(monthAgo.getDate() - 30);
  const [mode, setMode] = useState<'FLIGHT' | 'ROUTE'>('FLIGHT');
  const [flight, setFlight] = useState('');
  const [departure, setDeparture] = useState('');
  const [arrival, setArrival] = useState('');
  const [from, setFrom] = useState(localDay(monthAgo));
  const [to, setTo] = useState(today);
  const [validation, setValidation] = useState('');

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setValidation('');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to) || from > to) {
      setValidation('Начальная дата должна быть не позже конечной.');
      return;
    }
    if (mode === 'FLIGHT') {
      const code = flight.trim().toUpperCase().replace(/[\s-]/g, '');
      if (!/^[A-Z0-9]{2,3}\d{1,4}[A-Z]?$/.test(code)) {
        setValidation('Введите номер рейса, например SU100, SU-100 или 4V5023.');
        return;
      }
      onSubmit({ type: 'FLIGHT', flightIata: code, from, to });
      return;
    }
    const dep = departure.trim().toUpperCase();
    const arr = arrival.trim().toUpperCase();
    if (!/^[A-Z]{3}$/.test(dep) || !/^[A-Z]{3}$/.test(arr) || dep === arr) {
      setValidation('Укажите два разных трёхбуквенных кода аэропортов IATA.');
      return;
    }
    onSubmit({ type: 'ROUTE', departure: dep, arrival: arr, from, to });
  }

  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-7" aria-labelledby="analytics-filters-title">
      <div className="mb-5 flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-50 text-blue-600"><Search className="h-5 w-5" aria-hidden="true" /></div>
        <div><h2 id="analytics-filters-title" className="text-lg font-extrabold text-slate-900">Параметры анализа</h2><p className="text-sm text-slate-500">Выберите рейс или маршрут и период наблюдения</p></div>
      </div>
      <div role="group" aria-label="Тип анализа" className="mb-5 flex flex-wrap gap-2">
        <button type="button" aria-pressed={mode === 'FLIGHT'} onClick={() => { setMode('FLIGHT'); setValidation(''); }} className={`rounded-xl border px-4 py-2 text-sm font-semibold ${mode === 'FLIGHT' ? 'border-blue-600 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600'}`}>По номеру рейса</button>
        <button type="button" aria-pressed={mode === 'ROUTE'} onClick={() => { setMode('ROUTE'); setValidation(''); }} className={`rounded-xl border px-4 py-2 text-sm font-semibold ${mode === 'ROUTE' ? 'border-blue-600 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600'}`}>По маршруту</button>
      </div>
      <form noValidate onSubmit={submit} className="grid grid-cols-1 items-end gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {mode === 'FLIGHT' ? (
          <label className="sm:col-span-2 lg:col-span-1"><span className={labelClass}>Номер рейса</span><input className={fieldClass} aria-label="Номер рейса для анализа" value={flight} onChange={(e) => setFlight(e.target.value)} placeholder="SU100 или SU-100" autoComplete="off" maxLength={12} /></label>
        ) : (
          <>
            <label><span className={labelClass}>Откуда (IATA)</span><input className={fieldClass} aria-label="IATA вылета" value={departure} onChange={(e) => setDeparture(e.target.value.toUpperCase())} placeholder="SVO" maxLength={3} /></label>
            <label><span className={labelClass}>Куда (IATA)</span><input className={fieldClass} aria-label="IATA прилёта" value={arrival} onChange={(e) => setArrival(e.target.value.toUpperCase())} placeholder="LED" maxLength={3} /></label>
          </>
        )}
        <label><span className={labelClass}>Дата с</span><div className="relative"><CalendarDays className="pointer-events-none absolute right-3 top-3.5 h-4 w-4 text-blue-500" aria-hidden="true" /><input className={fieldClass} aria-label="Дата с" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></div></label>
        <label><span className={labelClass}>Дата по</span><input className={fieldClass} aria-label="Дата по" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <button type="submit" className="inline-flex h-[46px] items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 text-sm font-bold text-white shadow-lg shadow-blue-600/10 hover:bg-blue-700"><Plane className="h-4 w-4" aria-hidden="true" /> Анализировать</button>
      </form>
      {validation && <p role="alert" className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{validation}</p>}
      <p className="mt-4 text-xs text-slate-400">Даты включительно. Максимальный период согласуется с backend; дополнительные ограничения не установлены.</p>
    </section>
  );
}
