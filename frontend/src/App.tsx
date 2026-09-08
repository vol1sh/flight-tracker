import { useEffect, useState } from 'react';
import { Plane, Activity, ShieldCheck, Clock, AlertTriangle } from 'lucide-react';

interface SystemStatus {
  status: string;
  service: string;
  timestamp: string;
}

export default function App() {
  const [systemStatus, setSystemStatus] = useState<SystemStatus | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [flightInput, setFlightInput] = useState<string>('SU-100');

  useEffect(() => {
    fetch('/api/v1/system/ping')
      .then((res) => {
        if (!res.ok) throw new Error('API unavailable');
        return res.json();
      })
      .then((data: SystemStatus) => {
        setSystemStatus(data);
        setLoading(false);
      })
      .catch((err) => {
        console.warn('Backend ping failed:', err);
        setLoading(false);
      });
  }, []);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center p-6">
      <header className="w-full max-w-4xl flex items-center justify-between py-6 border-b border-slate-800 mb-8">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-blue-600/20 text-blue-400 rounded-xl border border-blue-500/30">
            <Plane className="w-8 h-8" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white">Flight Delay Tracker</h1>
            <p className="text-sm text-slate-400">Система мониторинга и предиктивной аналитики задержек авиарейсов</p>
          </div>
        </div>

        <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium border border-slate-800 bg-slate-900">
          <Activity className={`w-3.5 h-3.5 ${systemStatus?.status === 'UP' ? 'text-emerald-400 animate-pulse' : 'text-amber-400'}`} />
          <span>
            {loading ? 'Проверка API...' : systemStatus?.status === 'UP' ? 'API Онлайн' : 'Бэкенд недоступен'}
          </span>
        </div>
      </header>

      <main className="w-full max-w-4xl space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800/80 flex items-start gap-3">
            <ShieldCheck className="w-5 h-5 text-emerald-400 mt-0.5" />
            <div>
              <h3 className="text-sm font-semibold text-slate-200">Circuit Breaker</h3>
              <p className="text-xs text-slate-400 mt-1">Resilience4j защищает систему при сбоях внешнего API</p>
            </div>
          </div>
          <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800/80 flex items-start gap-3">
            <Clock className="w-5 h-5 text-cyan-400 mt-0.5" />
            <div>
              <h3 className="text-sm font-semibold text-slate-200">Redis Cache</h3>
              <p className="text-xs text-slate-400 mt-1">Мгновенный отклик и TTL 3 минуты для горячих данных рейсов</p>
            </div>
          </div>
          <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800/80 flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-400 mt-0.5" />
            <div>
              <h3 className="text-sm font-semibold text-slate-200">Аудит задержек</h3>
              <p className="text-xs text-slate-400 mt-1">PostgreSQL сохраняет историю изменений статусов и задержек</p>
            </div>
          </div>
        </div>

        <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 shadow-xl space-y-4">
          <h2 className="text-lg font-semibold text-white">Мониторинг статуса перелета</h2>
          <div className="flex gap-3">
            <input
              type="text"
              value={flightInput}
              onChange={(e) => setFlightInput(e.target.value.toUpperCase())}
              placeholder="Номер рейса (например, SU-100)"
              className="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-4 py-2.5 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono tracking-wider"
            />
            <button
              onClick={() => alert(`Запрос рейса ${flightInput}. Функционал поиска подключим на следующем шаге!`)}
              className="bg-blue-600 hover:bg-blue-500 text-white font-medium px-6 py-2.5 rounded-xl transition-all shadow-lg shadow-blue-600/20 active:scale-95"
            >
              Найти рейс
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}
