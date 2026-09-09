/**
 * Иконки современного пассажирского авиалайнера.
 * Чистая аэродинамика вместо бумажного треугольника.
 */

// Логотип лайнера — используется в шапке и заставке (направлен вверх-вправо для иконки)
export function PlaneLogo({ className = "w-6 h-6" }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" fill="currentColor" className={className}>
      {/* Фюзеляж и крылья лайнера */}
      <path d="M24 3C22.6 3 21.5 4.5 21.5 7.5V18.5L5 27V31L21.5 26.5V38.5L15.5 43.5V47L24 44.5L32.5 47V43.5L26.5 38.5V26.5L43 31V27L26.5 18.5V7.5C26.5 4.5 25.4 3 24 3Z" />
      {/* Левая турбина */}
      <rect x="15" y="22.5" width="2" height="6.5" rx="1" opacity="0.85" />
      {/* Правая турбина */}
      <rect x="31" y="22.5" width="2" height="6.5" rx="1" opacity="0.85" />
    </svg>
  );
}

// Лайнер для дуги маршрута (ориентирован вдоль горизонтальной траектории полёта)
export function PlaneRoute({ className = "w-7 h-7" }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" fill="currentColor" className={className} style={{ transform: 'rotate(90deg)' }}>
      <path d="M24 3C22.6 3 21.5 4.5 21.5 7.5V18.5L5 27V31L21.5 26.5V38.5L15.5 43.5V47L24 44.5L32.5 47V43.5L26.5 38.5V26.5L43 31V27L26.5 18.5V7.5C26.5 4.5 25.4 3 24 3Z" />
      <rect x="15" y="22.5" width="2" height="6.5" rx="1" opacity="0.85" />
      <rect x="31" y="22.5" width="2" height="6.5" rx="1" opacity="0.85" />
    </svg>
  );
}
