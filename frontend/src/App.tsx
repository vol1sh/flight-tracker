import { useEffect, useState, useRef } from 'react';
import type { FormEvent } from 'react';
import {
  Search,
  Clock,
  AlertCircle,
  History,
  CheckCircle2,
  ShieldAlert,
  ArrowRight,
  PlaneTakeoff,
  PlaneLanding,
  RotateCw,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { PlaneLogo } from './components/PlaneIcons';
import type { FlightResponse, FlightStatusLog, ActiveFlight } from './types/flight';

interface SystemStatus {
  status: string;
  service: string;
  timestamp: string;
}

function formatStatusLabel(status?: string | null): string {
  if (!status) return '—';
  const map: Record<string, string> = {
    SCHEDULED: 'Запланирован',
    ON_TIME: 'По расписанию',
    DELAYED: 'Задержан',
    CANCELLED: 'Отменён',
    UNKNOWN_DEGRADED: 'Автономный режим',
    LANDED: 'Приземлился',
  };
  return map[status.toUpperCase()] ?? status;
}

function statusColor(status?: string | null): string {
  switch (status?.toUpperCase()) {
    case 'DELAYED': return 'bg-amber-50 text-amber-600 border-amber-200';
    case 'CANCELLED': return 'bg-rose-50 text-rose-600 border-rose-200';
    case 'ON_TIME':
    case 'LANDED': return 'bg-emerald-50 text-emerald-600 border-emerald-200';
    case 'SCHEDULED': return 'bg-blue-50 text-blue-600 border-blue-200';
    default: return 'bg-slate-100 text-slate-600 border-slate-200';
  }
}

function formatDelay(minutes: number): string {
  if (minutes <= 0) return '0 мин';
  const mod10 = minutes % 10;
  const mod100 = minutes % 100;
  let unit = 'минут';
  if (mod100 < 11 || mod100 > 14) {
    if (mod10 === 1) unit = 'минута';
    else if (mod10 >= 2 && mod10 <= 4) unit = 'минуты';
  }
  return `+${minutes} ${unit}`;
}

/** Детализированный силуэт лайнера (вид сверху, нос ↑) */
function AirlinerSilhouette({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 100" fill="none" className={className} style={{ overflow: 'visible' as const }}>
      <defs>
        <linearGradient id="trailGrad" x1="0" y1="53" x2="0" y2="145" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#93c5fd" stopOpacity="0.35" />
          <stop offset="100%" stopColor="#93c5fd" stopOpacity="0" />
        </linearGradient>
      </defs>
      <line x1="17" y1="54" x2="17" y2="145" stroke="url(#trailGrad)" strokeWidth="0.8" />
      <line x1="47" y1="54" x2="47" y2="145" stroke="url(#trailGrad)" strokeWidth="0.8" />
      <path d="M28,88 Q28,94 32,94 Q36,94 36,88 L36,18 Q36,8 32,4 Q28,8 28,18 Z" fill="#2563eb" />
      <path d="M30.5,18 L30.5,86 Q32,90 33.5,86 L33.5,18 Q32,10 30.5,18 Z" fill="#3b82f6" opacity="0.25" />
      <path d="M30,15 Q32,8 34,15" fill="#60a5fa" opacity="0.65" />
      <path d="M28,40 L3,58 L3,61 L28,49 Z" fill="#1d4ed8" />
      <path d="M36,40 L61,58 L61,61 L36,49 Z" fill="#1d4ed8" />
      <rect x="1" y="57" width="3" height="5" rx="1" fill="#2563eb" opacity="0.6" />
      <rect x="60" y="57" width="3" height="5" rx="1" fill="#2563eb" opacity="0.6" />
      <ellipse cx="17" cy="49" rx="3" ry="5.5" fill="#3b82f6" />
      <ellipse cx="47" cy="49" rx="3" ry="5.5" fill="#3b82f6" />
      <circle cx="17" cy="44" r="1.6" fill="#1e40af" opacity="0.4" />
      <circle cx="47" cy="44" r="1.6" fill="#1e40af" opacity="0.4" />
      <path d="M28,78 L15,86 L15,88 L28,82 Z" fill="#3b82f6" />
      <path d="M36,78 L49,86 L49,88 L36,82 Z" fill="#3b82f6" />
      <rect x="30" y="76" width="4" height="12" rx="2" fill="#1d4ed8" />
      <line x1="30" y1="22" x2="30" y2="72" stroke="#bfdbfe" strokeWidth="0.6" strokeDasharray="1 1.8" opacity="0.3" />
      <line x1="34" y1="22" x2="34" y2="72" stroke="#bfdbfe" strokeWidth="0.6" strokeDasharray="1 1.8" opacity="0.3" />
    </svg>
  );
}

function SplashScreen({ phase }: { phase: 'flying' | 'fading' | 'done' }) {
  if (phase === 'done') return null;
  const runway = Array.from({ length: 9 }, (_, i) => {
    const t = i / 8;
    return {
      y: 175 - t * 110,
      half: 40 - t * 32,
      r: 3.0 - t * 2.0,
      dim: 1.0 - t * 0.55,
      delay: i * 0.05,
    };
  });

  return (
    <div
      className={`fixed inset-0 z-50 flex flex-col items-center justify-center overflow-hidden bg-[#f7f9fc] ${
        phase === 'fading' ? 'pointer-events-none' : ''
      }`}
      style={
        phase === 'fading'
          ? { animation: 'splashSlideUp 0.5s cubic-bezier(0.4, 0, 0.2, 1) forwards' }
          : undefined
      }
    >
      <svg
        viewBox="0 0 200 200"
        className="absolute w-72 h-72 sm:w-80 sm:h-80"
        style={{ animation: 'runwayFadeOut 0.4s ease-out 1.4s forwards' }}
      >
        <line
          x1="100" y1="180" x2="100" y2="65"
          stroke="#93c5fd"
          strokeWidth="0.5"
          strokeDasharray="3 3"
          opacity="0"
          style={{ animation: 'runwayLightIn 0.4s ease-out 0.15s both' }}
        />
        {runway.map((p, i) => (
          <g key={i} style={{ opacity: 0, animation: `runwayLightIn 0.25s ease-out ${p.delay}s both` }}>
            <circle cx={100 - p.half} cy={p.y} r={p.r} fill="#60a5fa" opacity={p.dim} />
            <circle cx={100 + p.half} cy={p.y} r={p.r} fill="#60a5fa" opacity={p.dim} />
            <circle cx={100 - p.half} cy={p.y} r={p.r * 3} fill="#3b82f6" opacity={p.dim * 0.06} />
            <circle cx={100 + p.half} cy={p.y} r={p.r * 3} fill="#3b82f6" opacity={p.dim * 0.06} />
          </g>
        ))}
      </svg>
      <div
        className="absolute w-12 h-12 rounded-full border-2 border-blue-400/50 pointer-events-none"
        style={{ animation: 'centerPulse 0.5s ease-out 1.1s both' }}
      />
      <div
        className="absolute pointer-events-none"
        style={{
          animation:
            'planeFlight 1.2s cubic-bezier(0.16, 1, 0.3, 1) 0.55s both, planeFadeOut 0.2s ease-out 1.65s forwards',
        }}
      >
        <div style={{ animation: 'flightOscillate 1.2s ease-in-out infinite' }}>
          <AirlinerSilhouette className="w-14 h-auto drop-shadow-[0_2px_8px_rgba(37,99,235,0.25)]" />
        </div>
      </div>
      <div className="relative z-10">
        <div
          className="w-20 h-20 rounded-3xl bg-gradient-to-br from-blue-500 via-blue-600 to-indigo-700 flex items-center justify-center shadow-2xl shadow-blue-500/30"
          style={{ animation: 'logoSquareReveal 0.45s ease-out 1.75s both' }}
        >
          <div className="-rotate-45">
            <PlaneLogo className="w-10 h-10 text-white drop-shadow-[0_2px_6px_rgba(0,0,0,0.2)]" />
          </div>
        </div>
        <div
          className="absolute inset-0 rounded-3xl border-2 border-blue-400 pointer-events-none"
          style={{ animation: 'logoWave 0.7s ease-out 1.95s both' }}
        />
      </div>
      <div className="relative z-10 text-center mt-7">
        <h2
          className="text-xl font-extrabold text-slate-900 tracking-tight"
          style={{ animation: 'textClipReveal 0.3s ease-out 2.1s both' }}
        >
          Flight Tracker
        </h2>
        <p
          className="text-sm text-slate-400 mt-1.5 tracking-wide"
          style={{ animation: 'entranceText 0.3s ease-out 2.3s both' }}
        >
          Мониторинг перелётов в реальном времени
        </p>
      </div>
    </div>
  );
}

function RouteArc() {
  return (
    <div className="relative flex items-center justify-center py-3 sm:py-0 w-full min-w-[120px] sm:min-w-[180px]">
      <svg
        className="w-full h-16"
        viewBox="0 0 200 55"
        fill="none"
        preserveAspectRatio="xMidYMid meet"
      >
        <defs>
          <linearGradient id="routeGrad" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#93c5fd" stopOpacity="0.4" />
            <stop offset="50%" stopColor="#2563eb" stopOpacity="0.9" />
            <stop offset="100%" stopColor="#93c5fd" stopOpacity="0.4" />
          </linearGradient>
          <filter id="planeShadow" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="2" stdDeviation="1.5" floodColor="#1d4ed8" floodOpacity="0.35" />
          </filter>
        </defs>
        <path
          d="M 10 42 Q 100 4 190 42"
          stroke="url(#routeGrad)"
          strokeWidth="1.8"
          strokeLinecap="round"
          className="route-dash"
        />
        <circle cx="10" cy="42" r="3.5" fill="#3b82f6" />
        <circle cx="10" cy="42" r="7" fill="none" stroke="#3b82f6" strokeWidth="0.5" opacity="0.25" />
        <circle cx="190" cy="42" r="3.5" fill="#3b82f6" />
        <circle cx="190" cy="42" r="7" fill="none" stroke="#3b82f6" strokeWidth="0.5" opacity="0.25" />
        <g transform="translate(100, 23) scale(0.85)" filter="url(#planeShadow)">
          <path
            d="M21 16v-2l-8-5V3.5A1.5 1.5 0 0 0 11.5 2 1.5 1.5 0 0 0 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5Z"
            fill="#2563eb"
            transform="rotate(90) translate(-11.5, -12)"
          />
        </g>
      </svg>
    </div>
  );
}

const RADAR_BLIPS = [
  { id: 'SU1492',  label: 'SU1492',  cx: 62,  cy: 38, rot: 35,  moveAnim: 'blipMove1', pingDelay: '0s' },
  { id: 'AFL105',  label: 'AFL105',  cx: 145, cy: 55, rot: -20, moveAnim: 'blipMove2', pingDelay: '1s' },
  { id: 'S72020',  label: 'S7 2020', cx: 48,  cy: 130, rot: 70, moveAnim: 'blipMove3', pingDelay: '2s' },
  { id: 'DP6143',  label: 'DP6143',  cx: 160, cy: 135, rot: -50, moveAnim: 'blipMove4', pingDelay: '0.5s' },
  { id: 'N4502',   label: 'N4 502',  cx: 105, cy: 162, rot: 15, moveAnim: 'blipMove5', pingDelay: '1.5s' },
];

function EmptyStateScreen() {
  return (
    <section className="animate-fade-in-up-d1 bg-white rounded-3xl border border-slate-200/80 p-8 sm:p-12 text-center shadow-[0_1px_3px_rgba(0,0,0,0.04),0_8px_24px_rgba(37,99,235,0.04)] overflow-hidden relative">
      <div className="max-w-md mx-auto flex flex-col items-center">
        <div className="relative w-[220px] h-[220px] sm:w-[260px] sm:h-[260px] flex items-center justify-center my-4">
          <svg
            viewBox="0 0 200 200"
            className="w-full h-full"
            style={{ filter: 'drop-shadow(0 4px 20px rgba(37, 99, 235, 0.08))' }}
          >
            <defs>
              <radialGradient id="radarBg" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="#ffffff" />
                <stop offset="60%" stopColor="#f0f5ff" />
                <stop offset="100%" stopColor="#e0eaff" />
              </radialGradient>
              <linearGradient id="sweepGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.30" />
                <stop offset="40%" stopColor="#3b82f6" stopOpacity="0.10" />
                <stop offset="100%" stopColor="#3b82f6" stopOpacity="0" />
              </linearGradient>
              <filter id="blipGlow" x="-100%" y="-100%" width="300%" height="300%">
                <feGaussianBlur stdDeviation="2" result="blur" />
                <feComposite in="SourceGraphic" in2="blur" operator="over" />
              </filter>
              <clipPath id="radarClip">
                <circle cx="100" cy="100" r="93" />
              </clipPath>
            </defs>
            <circle cx="100" cy="100" r="95" fill="url(#radarBg)" stroke="#c7d2fe" strokeWidth="2" />
            <circle cx="100" cy="100" r="95" fill="none" stroke="#3b82f6" strokeWidth="0.5" opacity="0.2" />
            <circle cx="100" cy="100" r="23" fill="none" stroke="#93c5fd" strokeWidth="0.5" opacity="0.4" />
            <circle cx="100" cy="100" r="46" fill="none" stroke="#93c5fd" strokeWidth="0.5" opacity="0.4" />
            <circle cx="100" cy="100" r="69" fill="none" stroke="#93c5fd" strokeWidth="0.5" opacity="0.4" />
            <circle cx="100" cy="100" r="92" fill="none" stroke="#93c5fd" strokeWidth="0.5" opacity="0.3" />
            <line x1="100" y1="6" x2="100" y2="194" stroke="#93c5fd" strokeWidth="0.4" opacity="0.35" />
            <line x1="6" y1="100" x2="194" y2="100" stroke="#93c5fd" strokeWidth="0.4" opacity="0.35" />
            <line x1="33" y1="33" x2="167" y2="167" stroke="#93c5fd" strokeWidth="0.3" opacity="0.2" />
            <line x1="167" y1="33" x2="33" y2="167" stroke="#93c5fd" strokeWidth="0.3" opacity="0.2" />
            <text x="100" y="15" textAnchor="middle" fill="#3b82f6" fontSize="7" fontFamily="monospace" opacity="0.45">N</text>
            <text x="100" y="195" textAnchor="middle" fill="#3b82f6" fontSize="7" fontFamily="monospace" opacity="0.45">S</text>
            <text x="11" y="103" textAnchor="middle" fill="#3b82f6" fontSize="7" fontFamily="monospace" opacity="0.45">W</text>
            <text x="189" y="103" textAnchor="middle" fill="#3b82f6" fontSize="7" fontFamily="monospace" opacity="0.45">E</text>
            <circle cx="100" cy="100" r="2.5" fill="#3b82f6" opacity="0.7" />
            <circle cx="100" cy="100" r="5" fill="none" stroke="#3b82f6" strokeWidth="0.5" opacity="0.25" />
            <g clipPath="url(#radarClip)">
              <g className="radar-sweep" style={{ transformOrigin: '100px 100px' }}>
                <path
                  d="M 100 100 L 100 5 A 95 95 0 0 1 157 17 Z"
                  fill="url(#sweepGrad)"
                  opacity="0.7"
                />
                <line x1="100" y1="100" x2="100" y2="7" stroke="#3b82f6" strokeWidth="1" opacity="0.5" />
              </g>
            </g>
            {RADAR_BLIPS.map((blip) => (
              <g
                key={blip.id}
                style={{ animation: `${blip.moveAnim} 20s ease-in-out infinite` }}
              >
                <circle
                  cx={blip.cx}
                  cy={blip.cy}
                  r="3"
                  fill="none"
                  stroke="#3b82f6"
                  strokeWidth="0.8"
                  opacity="0"
                  style={{
                    animation: `radarPingRing 4s ease-out ${blip.pingDelay} infinite`,
                  }}
                />
                <g
                  transform={`translate(${blip.cx}, ${blip.cy}) rotate(${blip.rot}) scale(0.35)`}
                  filter="url(#blipGlow)"
                  style={{
                    animation: `blipPing 4s ease-in-out ${blip.pingDelay} infinite`,
                  }}
                >
                  <path
                    d="M0,-12 L3,-4 L12,2 L3,3 L2,10 L0,8 L-2,10 L-3,3 L-12,2 L-3,-4 Z"
                    fill="#2563eb"
                  />
                </g>
                <text
                  x={blip.cx + 8}
                  y={blip.cy - 6}
                  fill="#2563eb"
                  fontSize="5"
                  fontFamily="monospace"
                  opacity="0.6"
                  style={{
                    animation: `blipPing 4s ease-in-out ${blip.pingDelay} infinite`,
                  }}
                >
                  {blip.label}
                </text>
                <line
                  x1={blip.cx}
                  y1={blip.cy}
                  x2={blip.cx - Math.cos((blip.rot * Math.PI) / 180) * 8}
                  y2={blip.cy + Math.sin((blip.rot * Math.PI) / 180) * 8}
                  stroke="#93c5fd"
                  strokeWidth="0.6"
                  opacity="0.35"
                />
              </g>
            ))}
          </svg>
        </div>
        <h3 className="text-xl font-extrabold text-slate-900 tracking-tight mt-2">
          Готовы к отслеживанию перелёта
        </h3>
        <p className="text-[14px] text-slate-500 mt-2 leading-relaxed">
          Введите номер рейса в строку поиска выше или выберите борт из списка ближайших рейсов, чтобы получить телеметрию, отклонения от расписания и историю статусов.
        </p>
      </div>
    </section>
  );
}

export default function App() {
  const [splash, setSplash] = useState<'flying' | 'fading' | 'done'>('flying');
  const [systemStatus, setSystemStatus] = useState<SystemStatus | null>(null);
  const [flightQuery, setFlightQuery] = useState('');
  const [flightData, setFlightData] = useState<FlightResponse | null>(null);
  const [historyLogs, setHistoryLogs] = useState<FlightStatusLog[]>([]);
  const [activeFlights, setActiveFlights] = useState<ActiveFlight[]>([]);
  const [isLoadingActive, setIsLoadingActive] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [mskClock, setMskClock] = useState('');

  const scrollRef = useRef<HTMLDivElement>(null);

  const scrollActive = (direction: 'left' | 'right') => {
    if (scrollRef.current) {
      const offset = direction === 'left' ? -280 : 280;
      scrollRef.current.scrollBy({ left: offset, behavior: 'smooth' });
    }
  };

  useEffect(() => {
    const t1 = setTimeout(() => setSplash('fading'), 2700);
    const t2 = setTimeout(() => setSplash('done'), 3200);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, []);

  useEffect(() => {
    const tick = () =>
      setMskClock(
        new Date().toLocaleTimeString('ru-RU', {
          timeZone: 'Europe/Moscow',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: false,
        }),
      );
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

  const fetchActiveFlights = async () => {
    setIsLoadingActive(true);
    try {
      const res = await fetch('/api/v1/flights/active');
      if (res.ok) {
        const data: ActiveFlight[] = await res.json();
        setActiveFlights(data);
      }
    } catch {
      // игнорируем ошибки списка активных
    } finally {
      setIsLoadingActive(false);
    }
  };

  useEffect(() => {
    fetch('/api/v1/system/ping')
      .then((r) => (r.ok ? r.json() : null))
      .then((d: SystemStatus | null) => setSystemStatus(d))
      .catch(() => setSystemStatus(null));
    fetchActiveFlights();
  }, []);

  const fetchFlight = async (iata: string) => {
    const code = iata.trim().toUpperCase();
    if (!code) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [fRes, hRes] = await Promise.all([
        fetch(`/api/v1/flights/${code}`),
        fetch(`/api/v1/flights/${code}/history`),
      ]);
      if (!fRes.ok)
        throw new Error(
          fRes.status === 404
            ? `Рейс ${code} не найден`
            : `Ошибка сервера (HTTP ${fRes.status})`,
        );
      const fData: FlightResponse = await fRes.json();
      const hData: FlightStatusLog[] = hRes.ok ? await hRes.json() : [];
      setFlightData(fData);
      setHistoryLogs(hData);
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Не удалось загрузить данные');
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
    } catch { return iso; }
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
    } catch { return iso; }
  };

  const degraded = Boolean(flightData?.degraded || flightData?.isDegraded);
  const depCity = flightData?.departureCity || flightData?.departureAirport || '—';
  const depAirportName = flightData?.departureAirportName || '';
  const arrCity = flightData?.arrivalCity || flightData?.arrivalAirport || '—';
  const arrAirportName = flightData?.arrivalAirportName || '';

  const fallbackCodes = ['ANA995', 'AAL9605', 'DAL1251', 'BAW117'];

  return (
    <div className="min-h-screen flex flex-col bg-[#f7f9fc] text-slate-900 bg-aviation-grid">
      <SplashScreen phase={splash} />

      {/* ── Header ── */}
      <header className="sticky top-0 z-30">
        <div className="h-1 bg-gradient-to-r from-blue-400 via-blue-600 to-indigo-500" />
        <div className="bg-white/95 backdrop-blur-lg border-b border-slate-200/60 shadow-[0_1px_3px_rgba(0,0,0,0.04)]">
          <div className="max-w-5xl mx-auto flex items-center justify-between px-5 sm:px-6 py-4">
            <div className="flex items-center gap-3.5">
              <div className="animate-float">
                <div className="w-11 h-11 rounded-2xl bg-gradient-to-br from-blue-500 to-blue-700 flex items-center justify-center shadow-lg shadow-blue-500/20">
                  <div className="-rotate-45">
                    <PlaneLogo className="w-5.5 h-5.5 text-white" />
                  </div>
                </div>
              </div>
              <div>
                <span className="font-extrabold text-lg tracking-tight text-slate-900 block leading-tight">
                  Flight Tracker
                </span>
                <span className="text-[12px] text-slate-400 tracking-wide mt-0.5 block">
                  Мониторинг перелётов
                </span>
              </div>
            </div>

            <div className="flex items-center gap-3 text-[13px]">
              <div className="flex items-center gap-2 bg-slate-50 px-3.5 py-2 rounded-xl border border-slate-200/80 font-mono">
                <Clock className="w-4 h-4 text-blue-500" />
                <span className="font-semibold text-slate-800 text-[14px]">{mskClock || '--:--:--'}</span>
                <span className="text-[10px] bg-blue-600 text-white px-1.5 py-0.5 rounded-md font-sans font-bold tracking-wide">
                  MSK
                </span>
              </div>
              <div className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white border border-slate-200/80 shadow-sm">
                <span
                  className={`w-2.5 h-2.5 rounded-full ${
                    systemStatus?.status === 'UP'
                      ? 'bg-emerald-500 shadow-[0_0_0_3px_rgba(16,185,129,0.15)]'
                      : 'bg-amber-400 shadow-[0_0_0_3px_rgba(245,158,11,0.15)]'
                  }`}
                />
                <span className="text-slate-700 text-[13px] font-medium">
                  {systemStatus?.status === 'UP' ? 'Онлайн' : 'Офлайн'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* ── Main Content ── */}
      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 py-6 sm:py-8 space-y-5">
        {/* Search */}
        <section className="animate-fade-in-up bg-white rounded-2xl border border-slate-200/80 p-5 shadow-[0_1px_3px_rgba(0,0,0,0.04),0_4px_12px_rgba(37,99,235,0.03)] hover:shadow-[0_2px_8px_rgba(0,0,0,0.06),0_8px_24px_rgba(37,99,235,0.05)] transition-shadow duration-300">
          <form onSubmit={onSearchSubmit} className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-[18px] h-[18px] text-slate-400 pointer-events-none" />
              <input
                id="flight-search-input"
                type="text"
                value={flightQuery}
                onChange={(e) => setFlightQuery(e.target.value.toUpperCase())}
                placeholder="Номер рейса, например 4V5023 или SAS4039"
                className="w-full bg-slate-50/80 border border-slate-200 rounded-xl pl-12 pr-4 py-3.5 text-[15px] text-slate-900 placeholder-slate-400 focus:outline-none focus:border-blue-500 focus:bg-white focus:ring-3 focus:ring-blue-100 transition-all font-mono uppercase tracking-wider"
              />
            </div>
            <button
              id="flight-search-btn"
              type="submit"
              disabled={isLoading || !flightQuery.trim()}
              className="bg-blue-600 hover:bg-blue-700 active:scale-[0.97] disabled:opacity-50 text-white text-[15px] font-semibold px-7 py-3.5 rounded-xl transition-all shadow-sm hover:shadow-md cursor-pointer flex items-center justify-center gap-2"
            >
              {isLoading ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Поиск…
                </>
              ) : (
                <>
                  Найти рейс
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          {/* Ближайшие рейсы: компактная горизонтальная лента в одну строку с кнопками прокрутки */}
          <div className="mt-4 pt-4 border-t border-slate-100">
            <div className="flex items-center justify-between gap-2 mb-2.5">
              <div className="flex items-center gap-2">
                <span className="text-[13px] font-semibold text-slate-700">
                  Ближайшие рейсы:
                </span>
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => scrollActive('left')}
                    className="w-6 h-6 rounded-md bg-slate-100 hover:bg-slate-200 text-slate-500 hover:text-slate-800 flex items-center justify-center transition-colors cursor-pointer"
                    title="Прокрутить назад"
                  >
                    <ChevronLeft className="w-3.5 h-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => scrollActive('right')}
                    className="w-6 h-6 rounded-md bg-slate-100 hover:bg-slate-200 text-slate-500 hover:text-slate-800 flex items-center justify-center transition-colors cursor-pointer"
                    title="Прокрутить вперед"
                  >
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              <button
                type="button"
                onClick={fetchActiveFlights}
                disabled={isLoadingActive}
                className="inline-flex items-center gap-1.5 text-[12px] text-blue-600 hover:text-blue-700 disabled:opacity-50 cursor-pointer font-medium"
              >
                <RotateCw className={`w-3.5 h-3.5 ${isLoadingActive ? 'animate-spin' : ''}`} />
                Обновить радар
              </button>
            </div>

            <div
              ref={scrollRef}
              className="flex items-center gap-2 overflow-x-auto pb-1.5 scroll-smooth"
              style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
            >
              {activeFlights.length > 0 ? (
                activeFlights.map((af) => {
                  const labelOrigin = af.originCity || af.originIata || '';
                  const labelDest = af.destCity || af.destIata || '';
                  const hasRoute = labelOrigin && labelDest;

                  return (
                    <button
                      key={af.callsign}
                      type="button"
                      onClick={() => {
                        setFlightQuery(af.callsign);
                        fetchFlight(af.callsign);
                      }}
                      className="group flex-shrink-0 px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-blue-50 hover:border-blue-200 border border-slate-200/80 transition-all cursor-pointer flex items-center gap-2 text-left whitespace-nowrap"
                    >
                      <span className="font-mono font-bold text-[13px] text-slate-800 group-hover:text-blue-600">
                        {af.callsign}
                      </span>
                      {hasRoute && (
                        <span className="text-[12px] text-slate-500 group-hover:text-slate-700">
                          {labelOrigin} → {labelDest}
                        </span>
                      )}
                    </button>
                  );
                })
              ) : (
                fallbackCodes.map((code) => (
                  <button
                    key={code}
                    type="button"
                    onClick={() => { setFlightQuery(code); fetchFlight(code); }}
                    className="flex-shrink-0 px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-blue-50 hover:text-blue-600 text-slate-600 transition-colors cursor-pointer border border-slate-200/80 text-[13px] font-mono whitespace-nowrap"
                  >
                    {code}
                  </button>
                ))
              )}
            </div>
          </div>
        </section>

        {/* Error */}
        {errorMessage && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-[14px] flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-rose-500 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {/* Degraded warning */}
        {degraded && (
          <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-700 text-[14px] flex items-center gap-3">
            <ShieldAlert className="w-5 h-5 text-amber-600 shrink-0" />
            <span>Внешний поставщик данных недоступен — отображаются кэшированные данные.</span>
          </div>
        )}

        {/* ── Состояние без рейса: экран ожидания с радарной орбитой и блипами ── */}
        {!flightData && !isLoading && !errorMessage && <EmptyStateScreen />}

        {/* ── Flight Card ── */}
        {flightData && (
          <section className="animate-fade-in-up-d1 bg-white rounded-2xl border border-slate-200/80 shadow-[0_1px_3px_rgba(0,0,0,0.04),0_4px_12px_rgba(37,99,235,0.03)] overflow-hidden">
            <div className="px-6 py-5 border-b border-slate-100 flex flex-wrap items-center justify-between gap-4 bg-gradient-to-r from-slate-50/80 to-white">
              <div className="flex items-center gap-3">
                <h1 className="text-[32px] font-extrabold font-mono tracking-wider text-slate-900 leading-none">
                  {flightData.flightIata}
                </h1>
                <span className={`inline-flex items-center text-[12px] px-3 py-1 rounded-lg font-semibold border ${statusColor(flightData.status)}`}>
                  {formatStatusLabel(flightData.status)}
                </span>
              </div>
              <div className="flex items-center gap-2.5 bg-white px-4 py-2 rounded-xl border border-slate-200 shadow-sm">
                <span className="text-slate-500 text-[13px] font-medium">Задержка:</span>
                <span className={`font-mono font-bold text-[14px] ${
                  flightData.delayMinutes > 0 ? 'text-amber-600' : 'text-emerald-600'
                }`}>
                  {formatDelay(flightData.delayMinutes)}
                </span>
              </div>
            </div>

            <div className="p-6">
              <div className="grid grid-cols-[1fr_auto_1fr] items-stretch gap-3 sm:gap-6">
                {/* Отправление (Выровнено по структуре и высоте) */}
                <div className="bg-gradient-to-b from-slate-50 to-white p-5 rounded-2xl border border-slate-200/70 shadow-sm flex flex-col justify-between">
                  <div>
                    <div className="flex items-center gap-1.5 text-[12px] text-slate-500 uppercase font-semibold tracking-wide">
                      <PlaneTakeoff className="w-4 h-4 text-blue-500" />
                      Вылет
                    </div>
                    {/* Фиксированная высота блока с кодом и именем аэропорта */}
                    <div className="mt-2.5 min-h-[66px] flex flex-col justify-start">
                      <span className="text-[28px] font-extrabold font-mono text-slate-900 leading-none">
                        {flightData.departureAirport}
                      </span>
                      <span className="text-[14px] font-bold text-slate-700 mt-1.5 line-clamp-2 leading-snug">
                        {depCity}
                      </span>
                      {depAirportName && depAirportName !== depCity && (
                        <span className="text-[12px] text-slate-400 mt-0.5 font-medium line-clamp-1">
                          {depAirportName}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="mt-4 pt-3 border-t border-slate-200/60 space-y-2.5">
                    <div className="flex justify-between text-[13px]">
                      <span className="text-slate-500">По расписанию</span>
                      <span className="font-mono font-semibold text-slate-800">{fmtTime(flightData.scheduledDeparture)}</span>
                    </div>
                    <div className="flex justify-between text-[13px]">
                      <span className="text-slate-500">Фактическое</span>
                      <span className="font-mono font-semibold text-blue-600">{fmtTime(flightData.actualDeparture)}</span>
                    </div>
                  </div>
                </div>

                {/* Центр: Траектория полета */}
                <div className="flex flex-col items-center justify-center">
                  <RouteArc />
                </div>

                {/* Прибытие (Выровнено по структуре и высоте) */}
                <div className="bg-gradient-to-b from-slate-50 to-white p-5 rounded-2xl border border-slate-200/70 shadow-sm flex flex-col justify-between">
                  <div>
                    <div className="flex items-center gap-1.5 text-[12px] text-slate-500 uppercase font-semibold tracking-wide">
                      <PlaneLanding className="w-4 h-4 text-blue-500" />
                      Прилёт
                    </div>
                    {/* Фиксированная высота блока с кодом и именем аэропорта */}
                    <div className="mt-2.5 min-h-[66px] flex flex-col justify-start">
                      <span className="text-[28px] font-extrabold font-mono text-slate-900 leading-none">
                        {flightData.arrivalAirport}
                      </span>
                      <span className="text-[14px] font-bold text-slate-700 mt-1.5 line-clamp-2 leading-snug">
                        {arrCity}
                      </span>
                      {arrAirportName && arrAirportName !== arrCity && (
                        <span className="text-[12px] text-slate-400 mt-0.5 font-medium line-clamp-1">
                          {arrAirportName}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="mt-4 pt-3 border-t border-slate-200/60 space-y-2.5">
                    <div className="flex justify-between text-[13px]">
                      <span className="text-slate-500">По расписанию</span>
                      <span className="font-mono font-semibold text-slate-800">{fmtTime(flightData.scheduledArrival)}</span>
                    </div>
                    <div className="flex justify-between text-[13px]">
                      <span className="text-slate-500">Фактическое</span>
                      <span className="font-mono font-semibold text-blue-600">{fmtTime(flightData.actualArrival)}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="px-6 py-3.5 bg-slate-50/60 border-t border-slate-100 text-[12px] font-mono text-slate-400 text-right">
              Обновлено: {fmtDate(flightData.lastUpdated)} в {fmtTime(flightData.lastUpdated)} MSK
            </div>
          </section>
        )}

        {/* ── History Log ── */}
        {flightData && (
          <section className="animate-fade-in-up-d2 bg-white rounded-2xl border border-slate-200/80 shadow-[0_1px_3px_rgba(0,0,0,0.04)] overflow-hidden">
            <div className="flex items-center gap-2.5 px-6 py-4 border-b border-slate-100 bg-gradient-to-r from-slate-50/80 to-white">
              <History className="w-4.5 h-4.5 text-blue-500" />
              <span className="font-bold text-[15px] text-slate-900">История изменений</span>
              {historyLogs.length > 0 && (
                <span className="ml-auto text-[13px] font-mono px-2.5 py-0.5 bg-slate-100 text-slate-600 rounded-lg">
                  {historyLogs.length}{' '}
                  {historyLogs.length === 1 ? 'запись' : historyLogs.length < 5 ? 'записи' : 'записей'}
                </span>
              )}
            </div>
            {historyLogs.length === 0 ? (
              <p className="text-[14px] text-slate-400 py-10 text-center">
                Изменений статуса не зафиксировано.
              </p>
            ) : (
              <div className="overflow-x-auto max-h-72 overflow-y-auto">
                <table className="w-full text-left border-collapse">
                  <thead className="sticky top-0 bg-slate-50/95 backdrop-blur z-10 border-b border-slate-200">
                    <tr className="text-slate-500 uppercase text-[12px] font-mono tracking-wider">
                      <th className="py-3.5 px-6 font-medium">Время (MSK)</th>
                      <th className="py-3.5 px-6 font-medium">Было</th>
                      <th className="py-3.5 px-6 font-medium">Стало</th>
                      <th className="py-3.5 px-6 text-right font-medium">Задержка</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-slate-700">
                    {historyLogs.map((log, idx) => (
                      <tr key={idx} className="hover:bg-blue-50/30 transition-colors">
                        <td className="py-3.5 px-6 font-mono font-medium text-slate-800 text-[13px] whitespace-nowrap">
                          {fmtDate(log.recordedAt)}{' '}
                          <span className="text-slate-400">{fmtTime(log.recordedAt)}</span>
                        </td>
                        <td className="py-3.5 px-6 text-slate-500 text-[13px]">
                          {formatStatusLabel(log.previousStatus)}
                        </td>
                        <td className="py-3.5 px-6 text-[13px]">
                          <span className="inline-flex items-center gap-1.5">
                            {idx === 0 && <CheckCircle2 className="w-4 h-4 text-emerald-500" />}
                            <span className="font-semibold text-slate-800">
                              {formatStatusLabel(log.newStatus)}
                            </span>
                          </span>
                        </td>
                        <td className="py-3.5 px-6 text-right font-mono font-bold text-[13px]">
                          <span className={log.delayMinutes > 0 ? 'text-amber-600' : 'text-emerald-600'}>
                            {formatDelay(log.delayMinutes)}
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

      {/* ── Footer ── */}
      <footer className="border-t border-slate-200 bg-white py-5 px-6 text-center text-[13px] text-slate-400">
        Flight Tracker · Время отображается по Москве (MSK / UTC+3)
      </footer>
    </div>
  );
}
