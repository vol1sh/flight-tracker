import { useEffect, useState, FormEvent } from 'react';
import {
  Plane,
  Activity,
  ShieldAlert,
  Clock,
  Search,
  ArrowRight,
  AlertCircle,
  History,
  Calendar,
  CheckCircle2,
  AlertTriangle
} from 'lucide-react';
import { FlightResponse, FlightStatusLog } from './types/flight';

interface SystemStatus {
  status: string;
  service: string;
  timestamp: string;
}

export default function App() {
  const [systemStatus, setSystemStatus] = useState<SystemStatus | null>(null);
  const [isSystemLoading, setIsSystemLoading] = useState<boolean>(true);

  const [flightQuery, setFlightQuery] = useState<string>('SU-100');
  const [flightData, setFlightData] = useState<FlightResponse | null>(null);
  const [historyLogs, setHistoryLogs] = useState<FlightStatusLog[]>([]);
  const [isLoadingFlight, setIsLoadingFlight] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/v1/system/ping')
      .then((res) => {
        if (!res.ok) throw new Error('API unavailable');
        return res.json();
      })
      .then((data: SystemStatus) => {
        setSystemStatus(data);
        setIsSystemLoading(false);
      })
      .catch((err) => {
        console.warn('Ping error:', err);
        setIsSystemLoading(false);
      });

    loadFlightData('SU-100');
  }, []);

  const loadFlightData = async (iata: string) => {
    const cleanIata = iata.trim().toUpperCase();
    if (!cleanIata) return;

    setIsLoadingFlight(true);
    setErrorMessage(null);

    try {
      const [flightRes, historyRes] = await Promise.all([
        fetch(`/api/v1/flights/${cleanIata}`),
        fetch(`/api/v1/flights/${cleanIata}/history`)
      ]);

      if (!flightRes.ok) {
        throw new Error(`Ошибка сервиса: HTTP ${flightRes.status}`);
      }

      const flightJson: FlightResponse = await flightRes.json();
      const historyJson: FlightStatusLog[] = historyRes.ok ? await historyRes.json() : [];

      setFlightData(flightJson);
      setHistoryLogs(historyJson);
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Не удалось получить данные о рейсе');
      setFlightData(null);
      setHistoryLogs([]);
    } finally {
      setIsLoadingFlight(false);
    }
  };

  const handleSearch = (e: FormEvent) => {
    e.preventDefault();
    loadFlightData(flightQuery);
  };

  const formatUtcTime = (isoString?: string | null) => {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }) + ' UTC';
    } catch {
      return isoString;
    }
  };

  const formatUtcDate = (isoString?: string | null) => {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      return date.toLocaleDateString('ru-RU', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' });
    } catch {
      return isoString;
    }
  };

  const isCircuitBreakerDegraded = Boolean(flightData?.degraded || flightData?.isDegraded);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center p-4 sm:p-6 lg:p-8">
      {/* Шапка приложения */}
      <header className="w-full max-w-5xl flex items-center justify-between pb-6 border-b border-slate-800 mb-8">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-blue-600/20 text-blue-400 rounded-xl border border-blue-500/30">
            <Plane className="w-7 h-7" />
          </div>
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-white flex items-center gap-2">
              Flight Delay Tracker
              <span className="text-xs font-normal px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800">
                v1.0
              </span>
            </h1>
            <p className="text-xs sm:text-sm text-slate-400">
              Мониторинг рейсов, кэширование и предиктивный аудит задержек
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium border border-slate-800 bg-slate-900">
          <Activity
            className={`w-3.5 h-3.5 ${
              systemStatus?.status === 'UP' ? 'text-emerald-400 animate-pulse' : 'text-amber-400'
            }`}
          />
          <span>
            {isSystemLoading
              ? 'Проверка API...'
              : systemStatus?.status === 'UP'
              ? 'REST API Онлайн'
              : 'Бэкенд недоступен'}
          </span>
        </div>
      </header>

      <main className="w-full max-w-5xl space-y-6">
        {/* Форма поиска рейса */}
        <section className="p-5 sm:p-6 rounded-2xl bg-slate-900 border border-slate-800 shadow-xl">
          <form onSubmit={handleSearch} className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3.5 top-3.5 w-5 h-5 text-slate-500" />
              <input
                type="text"
                value={flightQuery}
                onChange={(e) => setFlightQuery(e.target.value.toUpperCase())}
                placeholder="Введите номер рейса (например, SU-100)"
                className="w-full bg-slate-950 border border-slate-700 rounded-xl pl-11 pr-4 py-2.5 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono tracking-wider text-sm sm:text-base"
              />
            </div>
            <button
              type="submit"
              disabled={isLoadingFlight}
              className="bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium px-6 py-2.5 rounded-xl transition-all shadow-lg shadow-blue-600/20 active:scale-95 flex items-center justify-center gap-2"
            >
              {isLoadingFlight ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  <span>Поиск...</span>
                </>
              ) : (
                <>
                  <Search className="w-4 h-4" />
                  <span>Отследить</span>
                </>
              )}
            </button>
          </form>
        </section>

        {/* Сообщение об ошибке */}
        {errorMessage && (
          <div className="p-4 rounded-xl bg-red-950/40 border border-red-800/60 text-red-300 flex items-start gap-3">
            <AlertCircle className="w-5 h-5 mt-0.5 text-red-400 flex-shrink-0" />
            <div>
              <h3 className="font-semibold text-sm">Не удалось загрузить данные</h3>
              <p className="text-xs text-red-400/90 mt-0.5">{errorMessage}</p>
            </div>
          </div>
        )}

        {/* Баннер аварийной деградации Circuit Breaker */}
        {isCircuitBreakerDegraded && (
          <div className="p-4 rounded-xl bg-amber-950/40 border border-amber-800/60 text-amber-200 flex items-start gap-3">
            <ShieldAlert className="w-5 h-5 mt-0.5 text-amber-400 flex-shrink-0" />
            <div>
              <h3 className="font-semibold text-sm">Аварийный режим (Circuit Breaker Fallback)</h3>
              <p className="text-xs text-amber-300/80 mt-0.5">
                Внешний поставщик Aviation API временно недоступен. Отображаются последние сохраненные параметры из базы данных и кэша.
              </p>
            </div>
          </div>
        )}

        {/* Карточка статуса рейса */}
        {flightData && (
          <section className="p-6 rounded-2xl bg-slate-900 border border-slate-800 shadow-2xl space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-5">
              <div className="flex items-center gap-3">
                <span className="text-2xl sm:text-3xl font-black tracking-wider font-mono text-white">
                  {flightData.flightIata}
                </span>
                <span
                  className={`px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider ${
                    flightData.delayMinutes > 0
                      ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                      : 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                  }`}
                >
                  {flightData.status}
                </span>
              </div>

              {/* Индикатор величины задержки */}
              <div className="flex items-center gap-2">
                <Clock className="w-5 h-5 text-slate-400" />
                <span className="text-sm text-slate-400">Задержка:</span>
                <span
                  className={`text-base font-bold ${
                    flightData.delayMinutes > 0 ? 'text-amber-400' : 'text-emerald-400'
                  }`}
                >
                  {flightData.delayMinutes > 0 ? `+${flightData.delayMinutes} мин` : 'По расписанию'}
                </span>
              </div>
            </div>

            {/* Маршрут и время перелета */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 items-center">
              {/* Вылет */}
              <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80">
                <span className="text-xs text-slate-500 uppercase tracking-wider font-medium">Аэропорт вылета</span>
                <div className="text-3xl font-extrabold text-blue-400 mt-1 font-mono">{flightData.departureAirport}</div>
                <div className="mt-3 text-xs text-slate-400 space-y-1">
                  <div>План: <span className="text-slate-200">{formatUtcTime(flightData.scheduledDeparture)}</span></div>
                  <div>Факт: <span className="text-slate-200">{formatUtcTime(flightData.actualDeparture)}</span></div>
                </div>
              </div>

              {/* Маршрут со стрелкой */}
              <div className="flex flex-col items-center justify-center text-slate-500">
                <Plane className="w-8 h-8 text-blue-500 rotate-90 my-1 animate-pulse" />
                <div className="flex items-center gap-2 text-xs font-medium text-slate-400 mt-1">
                  <span>Прямой рейс</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </div>
              </div>

              {/* Прилет */}
              <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80">
                <span className="text-xs text-slate-500 uppercase tracking-wider font-medium">Аэропорт прилета</span>
                <div className="text-3xl font-extrabold text-cyan-400 mt-1 font-mono">{flightData.arrivalAirport}</div>
                <div className="mt-3 text-xs text-slate-400 space-y-1">
                  <div>План: <span className="text-slate-200">{formatUtcTime(flightData.scheduledArrival)}</span></div>
                  <div>Факт: <span className="text-slate-200">{formatUtcTime(flightData.actualArrival)}</span></div>
                </div>
              </div>
            </div>

            <div className="text-right text-xs text-slate-500 flex items-center justify-end gap-1.5">
              <Calendar className="w-3.5 h-3.5" />
              <span>Синхронизировано: {formatUtcDate(flightData.lastUpdated)} в {formatUtcTime(flightData.lastUpdated)}</span>
            </div>
          </section>
        )}

        {/* Таблица истории изменений и аудита (US-3) */}
        {flightData && (
          <section className="p-6 rounded-2xl bg-slate-900 border border-slate-800 shadow-xl space-y-4">
            <div className="flex items-center gap-2 text-white font-semibold text-base sm:text-lg border-b border-slate-800 pb-3">
              <History className="w-5 h-5 text-blue-400" />
              <h2>История аудита изменений рейса (PostgreSQL)</h2>
            </div>

            {historyLogs.length === 0 ? (
              <p className="text-xs sm:text-sm text-slate-400 py-4 text-center">
                Записей в журнале аудита не найдено.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs sm:text-sm border-collapse">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400 uppercase tracking-wider text-[11px]">
                      <th className="py-2.5 px-3">Время фиксации</th>
                      <th className="py-2.5 px-3">Предыдущий статус</th>
                      <th className="py-2.5 px-3">Новый статус</th>
                      <th className="py-2.5 px-3 text-right">Задержка</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    {historyLogs.map((log, index) => (
                      <tr key={index} className="hover:bg-slate-800/30 transition-colors">
                        <td className="py-3 px-3 text-slate-300">
                          {formatUtcDate(log.recordedAt)} {formatUtcTime(log.recordedAt)}
                        </td>
                        <td className="py-3 px-3 text-slate-400">
                          {log.previousStatus ? log.previousStatus : <span className="text-slate-600">—</span>}
                        </td>
                        <td className="py-3 px-3">
                          <span className="inline-flex items-center gap-1 text-slate-200">
                            {index === 0 ? (
                              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                            ) : (
                              <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
                            )}
                            {log.newStatus}
                          </span>
                        </td>
                        <td className="py-3 px-3 text-right font-bold">
                          <span className={log.delayMinutes > 0 ? 'text-amber-400' : 'text-emerald-400'}>
                            {log.delayMinutes > 0 ? `+${log.delayMinutes} мин` : '0 мин'}
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
    </div>
  );
}
