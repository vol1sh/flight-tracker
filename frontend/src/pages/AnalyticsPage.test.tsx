// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, expect, it, vi } from 'vitest';
import AnalyticsPage from './AnalyticsPage';

const sampleSummary = {
  scope: { type: 'FLIGHT', flightIata: 'SU100', departureIata: null, arrivalIata: null },
  period: { from: '2026-10-01', to: '2026-10-31' },
  sampleSize: 20, landedCount: 18, cancelledCount: 2, knownArrivalDelayCount: 18,
  averageDelayMinutes: 6.67, onTimeRate: 0.7778, cancellationRate: 0.1,
  reliabilityScore: 76.67, dataSufficient: true, message: null,
  dataSource: 'POSTGRESQL', lastUpdatedAt: '2026-11-01T12:00:00Z',
};

const execution = {
  flightIata: 'SU100', flightDate: '2026-10-14', departureIata: 'SVO', arrivalIata: 'LED',
  status: 'CANCELLED', arrivalDelayMinutes: null, lastSyncedAt: '2026-10-15T12:00:00Z',
};

const page = {
  content: [execution], number: 0, size: 20, totalElements: 1, totalPages: 1,
};

const response = (data: unknown, status = 200) => ({
  ok: status >= 200 && status < 300, status, json: async () => data,
} as Response);

function enterFlight(code = 'SU-100') {
  fireEvent.change(screen.getByLabelText('Номер рейса для анализа'), { target: { value: code } });
  fireEvent.change(screen.getByLabelText('Дата с'), { target: { value: '2026-10-01' } });
  fireEvent.change(screen.getByLabelText('Дата по'), { target: { value: '2026-10-31' } });
  fireEvent.click(screen.getByRole('button', { name: 'Анализировать' }));
}

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('ищет рейс, нормализует SU-100 и показывает доли как проценты', async () => {
  const request = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    return response(String(input).includes('/executions?') ? page : sampleSummary);
  });
  render(<AnalyticsPage />);
  enterFlight();
  await waitFor(() => expect(request).toHaveBeenCalledWith(
    '/api/v1/analytics/flights/SU100?from=2026-10-01&to=2026-10-31',
    expect.objectContaining({ signal: expect.any(AbortSignal) }),
  ));
  expect(await screen.findByLabelText('Без задержки по прибытии: 77,78%')).toBeInTheDocument();
  expect(screen.getByLabelText('Средняя задержка прибытия: 6,67 мин')).toBeInTheDocument();
  expect(screen.getByLabelText('Доля отмен: 10%')).toBeInTheDocument();
  expect(screen.getByText('POSTGRESQL')).toBeInTheDocument();
  expect(screen.getAllByText('18', { selector: 'dd' })).toHaveLength(2);
  expect(screen.getByRole('button', { name: 'История SU100 2026-10-14' })).toBeInTheDocument();
});

it('переключается на маршрут и отправляет IATA-коды вместо номера рейса', async () => {
  const request = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => response(String(input).includes('/executions?') ? { ...page, content: [] } : sampleSummary));
  render(<AnalyticsPage />);
  fireEvent.click(screen.getByRole('button', { name: 'По маршруту' }));
  fireEvent.change(screen.getByLabelText('IATA вылета'), { target: { value: 'svo' } });
  fireEvent.change(screen.getByLabelText('IATA прилёта'), { target: { value: 'led' } });
  fireEvent.change(screen.getByLabelText('Дата с'), { target: { value: '2026-10-01' } });
  fireEvent.change(screen.getByLabelText('Дата по'), { target: { value: '2026-10-31' } });
  fireEvent.click(screen.getByRole('button', { name: 'Анализировать' }));
  await waitFor(() => expect(request).toHaveBeenCalledTimes(2));
  const urls = request.mock.calls.map(([input]) => String(input));
  expect(urls).toContain('/api/v1/analytics/routes?from=2026-10-01&to=2026-10-31&departure=SVO&arrival=LED');
  expect(urls).toContain('/api/v1/analytics/executions?from=2026-10-01&to=2026-10-31&departure=SVO&arrival=LED&page=0&size=20');
});

it('проверяет период до запроса', () => {
  const request = vi.spyOn(globalThis, 'fetch');
  render(<AnalyticsPage />);
  fireEvent.change(screen.getByLabelText('Номер рейса для анализа'), { target: { value: 'SU100' } });
  fireEvent.change(screen.getByLabelText('Дата с'), { target: { value: '2026-11-01' } });
  fireEvent.change(screen.getByLabelText('Дата по'), { target: { value: '2026-10-01' } });
  fireEvent.click(screen.getByRole('button', { name: 'Анализировать' }));
  expect(screen.getByRole('alert')).toHaveTextContent('Начальная дата');
  expect(request).not.toHaveBeenCalled();
});

it('показывает недостаточную выборку и null как «Нет данных»', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => response(
    String(input).includes('/executions?') ? { ...page, content: [], totalElements: 0, totalPages: 0 } : {
      ...sampleSummary, sampleSize: 3, landedCount: 2, cancelledCount: 1,
      knownArrivalDelayCount: 0, averageDelayMinutes: null, onTimeRate: null,
      cancellationRate: null, reliabilityScore: null, dataSufficient: false,
      message: 'Недостаточно завершённых выполнений',
    },
  ));
  render(<AnalyticsPage />);
  enterFlight();
  expect(await screen.findByText('Недостаточно завершённых выполнений')).toBeInTheDocument();
  expect(screen.getByLabelText('Средняя задержка прибытия: Нет данных')).toBeInTheDocument();
  expect(screen.getByLabelText('Без задержки по прибытии: Нет данных')).toBeInTheDocument();
  expect(screen.getByText('В выбранном периоде нет сохранённых выполнений.')).toBeInTheDocument();
});

it.each([400, 500])('различает ошибки backend HTTP %s', async (status) => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(response({ message: 'fail' }, status));
  render(<AnalyticsPage />);
  enterFlight();
  const alerts = await screen.findAllByRole('alert');
  expect(alerts.some((element) => element.textContent?.includes(status === 400 ? 'Некорректные фильтры' : 'Сервер аналитики временно недоступен'))).toBe(true);
});

it('открывает CANCELLED и запрашивает историю по номеру и дате, показывает пустую историю', async () => {
  const request = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url.includes('/history?')) return response([]);
    return response(url.includes('/executions?') ? page : sampleSummary);
  });
  render(<AnalyticsPage />);
  enterFlight();
  fireEvent.click(await screen.findByRole('button', { name: 'История SU100 2026-10-14' }));
  await waitFor(() => expect(request).toHaveBeenCalledWith(
    '/api/v1/flights/SU100/history?date=2026-10-14', expect.objectContaining({ signal: expect.any(AbortSignal) }),
  ));
  expect(screen.getByText('Отменён')).toBeInTheDocument();
  expect(await screen.findByText('История изменений отсутствует.')).toBeInTheDocument();
});

it('показывает событие с previousStatus и newStatus из backend', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url.includes('/history?')) return response([{
      recordedAt: '2026-10-14T15:00:00Z', previousStatus: 'SCHEDULED', newStatus: 'CANCELLED', delayMinutes: 0,
    }]);
    return response(url.includes('/executions?') ? page : sampleSummary);
  });
  render(<AnalyticsPage />);
  enterFlight();
  fireEvent.click(await screen.findByRole('button', { name: 'История SU100 2026-10-14' }));
  expect(await screen.findByText('Запланирован → Отменён')).toBeInTheDocument();
});

it('загружает следующую страницу с page=1 вместо полной истории', async () => {
  const request = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url.includes('/executions?')) return response({
      ...page, number: url.includes('page=1') ? 1 : 0, totalPages: 2, totalElements: 21,
    });
    return response(sampleSummary);
  });
  render(<AnalyticsPage />);
  enterFlight();
  fireEvent.click(await screen.findByRole('button', { name: /Далее/ }));
  await waitFor(() => expect(request.mock.calls.some(([input]) => String(input).includes('page=1&size=20'))).toBe(true));
  expect(await screen.findByText('Страница 2 из 2')).toBeInTheDocument();
});

it('не показывает устаревший ответ после смены фильтров', async () => {
  let resolveOld!: (response: Response) => void;
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = String(input);
    if (url.includes('/flights/SU100?')) return new Promise<Response>((resolve) => { resolveOld = resolve; });
    if (url.includes('/flights/SU200?')) return Promise.resolve(response({ ...sampleSummary, averageDelayMinutes: 42 }));
    return Promise.resolve(response({ ...page, content: [] }));
  });
  render(<AnalyticsPage />);
  enterFlight('SU100');
  fireEvent.change(screen.getByLabelText('Номер рейса для анализа'), { target: { value: 'SU200' } });
  fireEvent.click(screen.getByRole('button', { name: 'Анализировать' }));
  expect(await screen.findByLabelText('Средняя задержка прибытия: 42 мин')).toBeInTheDocument();
  await act(async () => { resolveOld(response(sampleSummary)); });
  expect(screen.getByLabelText('Средняя задержка прибытия: 42 мин')).toBeInTheDocument();
  expect(screen.queryByLabelText('Средняя задержка прибытия: 6,67 мин')).not.toBeInTheDocument();
});

it('различает сетевой отказ и недостаток данных', async () => {
  vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
  render(<AnalyticsPage />);
  enterFlight();
  const alerts = await screen.findAllByRole('alert');
  expect(alerts.some((element) => element.textContent?.includes('Нет соединения с backend'))).toBe(true);
  expect(screen.queryByText('Недостаточно завершённых выполнений')).not.toBeInTheDocument();
});
