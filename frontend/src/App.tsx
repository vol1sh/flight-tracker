import { useEffect, useState, FormEvent } from 'react';
import {
  Search,
  Clock,
  AlertCircle,
  History,
  CheckCircle2,
  ShieldAlert,
  MapPin,
  ArrowRight,
  Radio,
  RefreshCw
} from 'lucide-react';
import { AirlinerLogoIcon, DetailedFlightAirliner } from './components/AirlinerIcons';
import type { FlightResponse, FlightStatusLog, ActiveFlight } from './types/flight';

interface SystemStatus {
  status: string;
  service: string;
  timestamp: string;
}

function DetailedRouteArc() {
  return (
    <div className="relative flex items-center justify-center py-4 sm:py-0 w-full min-w-[120px] sm:min-w-[160px]">
      <svg
        className="w-full h-16"
        viewBox="0 0 220 60"
        fill="none"
        preserveAspectRatio="xMidYMid meet"
      >
        <path
          d="M 12 46 Q 110 4 208 46"
          stroke="url(#routeGradientLight)"
          strokeWidth="2"
          strokeLinecap="round"
          className="route-path-dash"
        />
        <defs>
          <linearGradient id="routeGradientLight" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#93c5fd" stopOpacity="0.4" />
            <stop offset="50%" stopColor="#2563eb" stopOpacity="1" />
            <stop offset="100%" stopColor="#93c5fd" stopOpacity="0.4" />
          </linearGradient>
        </defs>
      </svg>

      <div className="absolute left-1/2 top-[30%] animate-plane-enroute pointer-events-none">
        <DetailedFlightAirliner className="w-9 h-9 text-blue-600" />
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    DELAYED: 'bg-amber-50 text-amber-700 border-amber-200 ring-1 ring-amber-400/20',
    CANCELLED: 'bg-rose-50 text-rose-700 border-rose-200 ring-1 ring-rose-400/20',
    ON_TIME: 'bg-emerald-50 text-emerald-700 border-emerald-200 ring-1 ring-emerald-400/20',
    SCHEDULED: 'bg-blue-50 text-blue-700 border-blue-200 ring-1 ring-blue-400/20',
  };
  const label: Record<string, string> = {
    DELAYED: 'Задержан',
    CANCELLED: 'Отменён',
    ON_TIME: 'По расписанию',
    SCHEDULED: 'Запланирован',
  };

  const currentStyle = styles[status] ?? styles.ON_TIME;
  const currentLabel = label[status] ?? status;

  return (
    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider border ${currentStyle}`}>
      <span className="w-1.5 h-1.5 rounded-full bg-current" />
      {currentLabel}
    </span>
  );
}

export default function App() {
  const [isInitializing, setIsInitializing] = useState(true);
  const [systemStatus, setSystemStatus] = useState<SystemStatus | null>(null);
  const [flightQuery, setFlightQuery] = useState('AAL1033');
  const [flightData, setFlightData] = useState<FlightResponse | null>(null);
  const [historyLogs, setHistoryLogs] = useState<FlightStatusLog[]>([]);
  const [activeFlights, setActiveFlights] = useState<ActiveFlight[]>([]);
  const [isLoadingActive, setIsLoadingActive] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [mskClock, setMskClock] = useState('');

  useEffect(() => {
    const updateTime = () =>
      setMskClock(
        new Date().toLocaleTimeString('ru-RU', {
          timeZone: 'Europe/Moscow',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: false,
        })
      );
    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  const loadActiveFlights = async () => {
    setIsLoadingActive(true);
    try {
      const res = await fetch('/api/v1/flights/active');
      if (res.ok) {
        const data: ActiveFlight[] = await res.json();
        setActiveFlights(data);
        if (data.length > 0 && !flightData) {
          fetchFlight(data[0].callsign);
        }
      }
    } catch {
      // Игнорируем сетевые сбои опроса списка
    } finally {
      setIsLoadingActive(false);
    }
  };

  useEffect(() => {
    fetch('/api/v1/system/ping')
      .then((r) => (r.ok ? r.json() : null))
      .then((d: SystemStatus | null) => setSystemStatus(d))
      .catch(() => setSystemStatus(null));

    loadActiveFlights();

    const loaderTimeout = setTimeout(() => {
      setIsInitializing(false);
    }, 850);

    return () => clearTimeout(loaderTimeout);
  }, []);

  const fetchFlight = async (codeToSearch: string) => {
    const code = codeToSearch.trim().toUpperCase();
    if (!code) return;
    setFlightQuery(code);
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [fRes, hRes] = await Promise.all([
        fetch(`/api/v1/flights/${code}`),
        fetch(`/api/v1/flights/${code}/history`),
      ]);
      if (!fRes.ok) {
        throw new Error(
          fRes.status === 404 ? `Рейс ${code} сейчас не находится в активном воздушном коридоре` : `Сбой шлюза (HTTP ${fRes.status})`
        );
      }
      const fData: FlightResponse = await fRes.json();
      const hData: FlightStatusLog[] = hRes.ok ? await hRes.json() : [];
      setFlightData(fData);
      setHistoryLogs(hData);
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Не удалось связаться со службой телеметрии');
      setFlightData(null);
      setHistoryLogs([]);
    } finally {
      setIsLoading(false);
    }
  };

  const onSearchSubmit = (e: FormEvent) => {
    e.preventDefault();
    fetchFlight(flightQuery);
  };

  const fmtTime = (iso?: string | null) => {
    if (!iso) return '—';
    try {
      return new Date(iso).toLocaleTimeString('ru-RU', {
        timeZone: 'Europe/Moscow',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      });
    } catch {
      return iso;
    }
  };

  const fmtDate = (iso?: string | null) => {
    if (!iso) return '—';
    try {
      return new Date(iso).toLocaleDateString('ru-RU', {
        timeZone: 'Europe/Moscow',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      });
    } catch {
      return iso;
    }
  };

  const degraded = Boolean(flightData?.degraded || flightData?.isDegraded);

  return (
    <div className="min-h-screen flex flex-col bg-[#f8fafc] text-slate-900 bg-aviation-grid">
      {/* Сплэш-экран */}
      <div
        className={`fixed inset-0 z-50 flex flex-col items-center justify-center bg-white transition-all duration-700 ease-out ${
          isInitializing ? 'opacity-100' : 'opacity-0 pointer-events-none scale-105'
        }`}
      >
        <div className="relative flex items-center justify-center mb-6">
          <div className="w-20 h-20 rounded-full border border-blue-200 animate-ping absolute" />
          <div className="w-16 h-16 rounded-full border-t-2 border-r-2 border-blue-600 animate-radar absolute" />
          <div className="w-14 h-14 rounded-2xl bg-blue-50 border border-blue-100 flex items-center justify-center shadow-md">
            <AirlinerLogoIcon className="w-8 h-8 text-blue-600" />
          </div>
        </div>
        <div className="space-y-1 text-center font-mono">
          <p className="text-xs tracking-widest text-blue-600 uppercase font-bold flex items-center gap-1.5 justify-center">
            <Radio className="w-3.5 h-3.5 animate-pulse" />
            Радарная сеть OpenSky Network
          </p>
          <h2 className="text-lg font-bold text-slate-900">Flight Tracker</h2>
        </div>
      </div>

      {/* Шапка */}
      <header className="sticky top-0 z-30 bg-white/90 backdrop-blur-md border-b border-slate-200/90 shadow-sm">
        <div className="max-w-5xl mx-auto flex items-center justify-between px-4 sm:px-6 py-3.5">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-blue-50 border border-blue-100 shadow-sm">
              <AirlinerLogoIcon className="w-6 h-6 text-blue-600" />
            </div>
            <div>
              <span className="font-bold text-base tracking-tight text-slate-900 block leading-tight">
                Flight Tracker
              </span>
              <span className="text-[11px] font-mono text-slate-500 uppercase tracking-wider">
                Мониторинг перелётов
              </span>
            </div>
          </div>

          <div className="flex items-center gap-3 sm:gap-4 text-xs font-mono">
            <div className="flex items-center gap-2 bg-slate-50 px-3 py-1.5 rounded-lg border border-slate-200 text-slate-700 shadow-inner">
              <Clock className="w-3.5 h-3.5 text-blue-600" />
              <span className="font-semibold text-slate-900">{mskClock || '--:--:--'}</span>
              <span className="text-[10px] bg-blue-100 text-blue-700 px-1 py-0.5 rounded font-sans font-bold">
                MSK
              </span>
            </div>

            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white border border-slate-200 shadow-sm">
              <span
                className={`w-2 h-2 rounded-full ${
                  systemStatus?.status === 'UP' ? 'bg-emerald-500 ring-4 ring-emerald-100' : 'bg-amber-500'
                }`}
              />
              <span className="text-slate-600 font-sans text-xs">Онлайн</span>
            </div>
          </div>
        </div>
      </header>

      {/* Контент */}
      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 py-8 space-y-6">
        {/* Поисковая панель */}
        <section className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-sm hover:shadow-md transition-shadow">
          <form onSubmit={onSearchSubmit} className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
              <input
                type="text"
                value={flightQuery}
                onChange={(e) => setFlightQuery(e.target.value.toUpperCase())}
                placeholder="Позывной борта или номер рейса (например, AAL1033, DAL1251)"
                className="w-full bg-slate-50/70 border border-slate-200 rounded-xl pl-11 pr-4 py-3 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:border-blue-600 focus:bg-white focus:ring-4 focus:ring-blue-100 transition-all font-mono uppercase tracking-wider"
              />
            </div>
            <button
              type="submit"
              disabled={isLoading}
              className="bg-blue-600 hover:bg-blue-700 active:scale-[0.98] disabled:opacity-50 text-white text-sm font-semibold px-7 py-3 rounded-xl transition-all shadow-sm hover:shadow-blue-500/20 cursor-pointer flex items-center justify-center gap-2"
            >
              {isLoading ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                  <span>Поиск...</span>
                </>
              ) : (
                <>
                  <span>Найти рейс</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          {/* Реальные активные рейсы прямо из OpenSky */}
          <div className="mt-4 pt-4 border-t border-slate-100">
            <div className="flex items-center justify-between text-xs text-slate-500 font-mono mb-2">
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                Сейчас в воздухе (OpenSky Network):
              </span>
              <button
                type="button"
                onClick={loadActiveFlights}
                disabled={isLoadingActive}
                className="hover:text-blue-600 flex items-center gap-1 cursor-pointer transition-colors"
              >
                <RefreshCw className={`w-3 h-3 ${isLoadingActive ? 'animate-spin' : ''}`} />
                <span>Обновить радар</span>
              </button>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {activeFlights.length === 0 ? (
                <span className="text-xs text-slate-400 font-mono">Сканирование воздушных коридоров...</span>
              ) : (
                activeFlights.map((item) => (
                  <button
                    key={item.callsign}
                    type="button"
                    onClick={() => fetchFlight(item.callsign)}
                    className="group px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-blue-50 hover:border-blue-200 border border-slate-200 transition-all text-left cursor-pointer flex items-center gap-2"
                  >
                    <span className="font-mono font-bold text-slate-800 group-hover:text-blue-600 text-xs">
                      {item.callsign}
                    </span>
                    <span className="text-[11px] text-slate-400 group-hover:text-blue-500">
                      {item.originCity} → {item.destCity}
                    </span>
                  </button>
                ))
              )}
            </div>
          </div>
        </section>

        {errorMessage && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-3 shadow-sm">
            <AlertCircle className="w-5 h-5 text-rose-500 shrink-0" />
            <span className="font-medium">{errorMessage}</span>
          </div>
        )}

        {degraded && (
          <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-center gap-3 shadow-sm">
            <ShieldAlert className="w-5 h-5 text-amber-600 shrink-0" />
            <span>Внешний поставщик недоступен — активен автономный режим кэширования (Circuit Breaker).</span>
          </div>
        )}

        {/* Карточка рейса с понятными городами */}
        {flightData && (
          <section className="bg-white rounded-2xl border border-slate-200/90 shadow-sm overflow-hidden">
            <div className="px-6 py-5 border-b border-slate-100 flex flex-wrap items-center justify-between gap-4 bg-slate-50/40">
              <div className="flex items-center gap-3.5">
                <h1 className="text-3xl font-extrabold font-mono tracking-wider text-slate-900">
                  {flightData.flightIata}
                </h1>
                <StatusBadge status={flightData.status} />
              </div>

              <div className="flex items-center gap-3 bg-white px-3.5 py-1.5 rounded-xl border border-slate-200 shadow-sm text-sm">
                <span className="text-slate-500 text-xs uppercase font-medium">Отклонение от расписания:</span>
                <span
                  className={`font-mono font-bold ${
                    flightData.delayMinutes > 0 ? 'text-amber-600' : 'text-emerald-600'
                  }`}
                >
                  {flightData.delayMinutes > 0 ? `+${flightData.delayMinutes} минут` : 'Без задержки (0 минут)'}
                </span>
              </div>
            </div>

            <div className="p-6">
              <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 sm:gap-6">
                {/* Пункт отправления: IATA, Город и Название аэропорта */}
                <div className="bg-slate-50 p-5 rounded-2xl border border-slate-200/80">
                  <div className="flex items-center gap-1.5 text-xs text-blue-600 uppercase font-bold tracking-wider">
                    <MapPin className="w-3.5 h-3.5 text-blue-600" />
                    Пункт отправления
                  </div>
                  <div className="flex items-baseline gap-2 mt-2">
                    <span className="text-3xl font-black font-mono text-slate-900">
                      {flightData.departureAirport}
                    </span>
                    <span className="text-base font-bold text-slate-700">
                      {flightData.departureCity || 'Город вылета'}
                    </span>
                  </div>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {flightData.departureAirportName || 'Аэропорт вылета'}
                  </p>

                  <div className="mt-4 pt-3 border-t border-slate-200/80 space-y-1.5 text-xs">
                    <div className="flex justify-between">
                      <span className="text-slate-500">Запланированное время (MSK)</span>
                      <span className="font-mono font-semibold text-slate-800">
                        {fmtTime(flightData.scheduledDeparture)}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-500">Фактическое время (MSK)</span>
                      <span className="font-mono font-semibold text-blue-600">
                        {fmtTime(flightData.actualDeparture)}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex flex-col items-center justify-center">
                  <DetailedRouteArc />
                </div>

                {/* Пункт назначения: IATA, Город и Название аэропорта */}
                <div className="bg-slate-50 p-5 rounded-2xl border border-slate-200/80">
                  <div className="flex items-center gap-1.5 text-xs text-blue-600 uppercase font-bold tracking-wider">
                    <MapPin className="w-3.5 h-3.5 text-blue-600" />
                    Пункт назначения
                  </div>
                  <div className="flex items-baseline gap-2 mt-2">
                    <span className="text-3xl font-black font-mono text-slate-900">
                      {flightData.arrivalAirport}
                    </span>
                    <span className="text-base font-bold text-slate-700">
                      {flightData.arrivalCity || 'Город назначения'}
                    </span>
                  </div>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {flightData.arrivalAirportName || 'Аэропорт прибытия'}
                  </p>

                  <div className="mt-4 pt-3 border-t border-slate-200/80 space-y-1.5 text-xs">
                    <div className="flex justify-between">
                      <span className="text-slate-500">Запланированное время (MSK)</span>
                      <span className="font-mono font-semibold text-slate-800">
                        {fmtTime(flightData.scheduledArrival)}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-500">Фактическое время (MSK)</span>
                      <span className="font-mono font-semibold text-blue-600">
                        {fmtTime(flightData.actualArrival)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="px-6 py-3 bg-slate-50 border-t border-slate-100 text-xs font-mono text-slate-400 text-right">
              Информация обновлена: {fmtDate(flightData.lastUpdated)} в {fmtTime(flightData.lastUpdated)} (Московское время)
            </div>
          </section>
        )}

        {/* Журнал изменений */}
        {flightData && (
          <section className="bg-white rounded-2xl border border-slate-200/90 shadow-sm overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/40">
              <div className="flex items-center gap-2">
                <History className="w-4 h-4 text-blue-600" />
                <span className="font-bold text-sm text-slate-900">Журнал истории изменений</span>
              </div>
              <span className="text-xs font-mono px-2.5 py-0.5 bg-slate-100 text-slate-600 rounded-lg">
                Записей в журнале: {historyLogs.length}
              </span>
            </div>

            {historyLogs.length === 0 ? (
              <p className="text-xs text-slate-400 py-8 text-center font-mono">
                Изменений статуса пока не зафиксировано.
              </p>
            ) : (
              <div className="overflow-x-auto max-h-64 overflow-y-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="sticky top-0 bg-slate-50 z-10">
                    <tr className="border-b border-slate-200 text-slate-500 uppercase text-[11px] font-mono tracking-wider">
                      <th className="py-3 px-6">Время фиксации (MSK)</th>
                      <th className="py-3 px-6">Предыдущий статус</th>
                      <th className="py-3 px-6">Текущий статус</th>
                      <th className="py-3 px-6 text-right">Величина задержки</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 font-mono text-slate-700">
                    {historyLogs.map((log, idx) => (
                      <tr key={idx} className="hover:bg-blue-50/40 transition-colors">
                        <td className="py-3 px-6 font-medium text-slate-900">
                          {fmtDate(log.recordedAt)} <span className="text-slate-500 font-normal">{fmtTime(log.recordedAt)}</span>
                        </td>
                        <td className="py-3 px-6 text-slate-400">{log.previousStatus || '—'}</td>
                        <td className="py-3 px-6">
                          <span className="inline-flex items-center gap-1.5">
                            {idx === 0 && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />}
                            <span className="font-semibold text-slate-800">
                              {log.newStatus === 'ON_TIME' ? 'По расписанию' : log.newStatus === 'DELAYED' ? 'Задержан' : log.newStatus}
                            </span>
                          </span>
                        </td>
                        <td className="py-3 px-6 text-right font-bold">
                          <span className={log.delayMinutes > 0 ? 'text-amber-600' : 'text-emerald-600'}>
                            {log.delayMinutes > 0 ? `+${log.delayMinutes} минут` : 'Без задержки (0 минут)'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}
      </main>

      <footer className="border-t border-slate-200 bg-white py-4 px-6 text-center text-xs text-slate-400 font-mono">
        Flight Delay Tracker Service • Реальная телеметрия OpenSky Network • Время по Москве (MSK)
      </footer>
    </div>
  );
}
