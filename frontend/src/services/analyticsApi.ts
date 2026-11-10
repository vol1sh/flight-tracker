import type { FlightStatusLog } from '../types/flight';
import type { AnalyticsExecution, AnalyticsFilter, AnalyticsPage, AnalyticsSummary } from '../types/analytics';

export type AnalyticsErrorType = 'bad-request' | 'server' | 'network' | 'format' | 'http';

export class AnalyticsApiError extends Error {
  constructor(public readonly kind: AnalyticsErrorType, public readonly status?: number) {
    const messages: Record<AnalyticsErrorType, string> = {
      'bad-request': 'Некорректные фильтры. Проверьте рейс, маршрут и период.',
      server: 'Сервер аналитики временно недоступен. Попробуйте позже.',
      network: 'Нет соединения с backend. Проверьте, запущен ли сервер.',
      format: 'Ответ backend не соответствует согласованному формату аналитики.',
      http: `Запрос не выполнен (HTTP ${status ?? 'неизвестен'}).`,
    };
    super(messages[kind]);
    this.name = 'AnalyticsApiError';
  }
}

async function getJson(url: string, signal?: AbortSignal): Promise<unknown> {
  let response: Response;
  try {
    response = await fetch(url, { signal });
  } catch (error) {
    if (signal?.aborted || (error instanceof Error && error.name === 'AbortError')) throw error;
    throw new AnalyticsApiError('network');
  }
  if (!response.ok) {
    if (response.status === 400) throw new AnalyticsApiError('bad-request', 400);
    if (response.status >= 500) throw new AnalyticsApiError('server', response.status);
    throw new AnalyticsApiError('http', response.status);
  }
  try {
    return await response.json() as unknown;
  } catch {
    throw new AnalyticsApiError('format');
  }
}

function periodParams(from: string, to: string): URLSearchParams {
  return new URLSearchParams({ from, to });
}

export function getFlightAnalytics(
  flightIata: string, from: string, to: string, signal?: AbortSignal,
): Promise<AnalyticsSummary> {
  return getJson(`/api/v1/analytics/flights/${encodeURIComponent(flightIata)}?${periodParams(from, to)}`, signal)
    .then(validateSummary);
}

export function getRouteAnalytics(
  departure: string, arrival: string, from: string, to: string, signal?: AbortSignal,
): Promise<AnalyticsSummary> {
  const params = periodParams(from, to);
  params.set('departure', departure);
  params.set('arrival', arrival);
  return getJson(`/api/v1/analytics/routes?${params}`, signal).then(validateSummary);
}

export function getExecutions(
  filter: AnalyticsFilter, page: number, size = 20, signal?: AbortSignal,
): Promise<AnalyticsPage<AnalyticsExecution>> {
  const params = periodParams(filter.from, filter.to);
  if (filter.type === 'FLIGHT') params.set('flightIata', filter.flightIata);
  else {
    params.set('departure', filter.departure);
    params.set('arrival', filter.arrival);
  }
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson(`/api/v1/analytics/executions?${params}`, signal).then(validateExecutionsPage);
}

export async function getExecutionHistory(
  flightIata: string, flightDate: string, signal?: AbortSignal,
): Promise<FlightStatusLog[]> {
  const params = new URLSearchParams({ date: flightDate });
  const data = await getJson(`/api/v1/flights/${encodeURIComponent(flightIata)}/history?${params}`, signal);
  if (!Array.isArray(data) || !data.every((item) => item && typeof item.recordedAt === 'string' && typeof item.newStatus === 'string')) {
    throw new AnalyticsApiError('format');
  }
  return data as FlightStatusLog[];
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function nonNegativeNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

function nullableNumber(value: unknown): value is number | null {
  return value === null || nonNegativeNumber(value);
}

function validateSummary(data: unknown): AnalyticsSummary {
  if (!isObject(data) || !isObject(data.scope) || !isObject(data.period)
    || !nonNegativeNumber(data.sampleSize) || !nonNegativeNumber(data.landedCount)
    || !nonNegativeNumber(data.cancelledCount) || !nonNegativeNumber(data.knownArrivalDelayCount)
    || typeof data.dataSufficient !== 'boolean'
    || !nullableNumber(data.averageDelayMinutes) || !nullableNumber(data.onTimeRate)
    || !nullableNumber(data.cancellationRate) || !nullableNumber(data.reliabilityScore)
    || data.dataSource !== 'POSTGRESQL'
    || !(data.lastUpdatedAt === null || typeof data.lastUpdatedAt === 'string')
    || !(data.message === null || typeof data.message === 'string')) {
    throw new AnalyticsApiError('format');
  }
  // Backend returns rates in the 0..1 range, not percentages.
  if ((typeof data.onTimeRate === 'number' && data.onTimeRate > 1)
    || (typeof data.cancellationRate === 'number' && data.cancellationRate > 1)
    || (typeof data.reliabilityScore === 'number' && data.reliabilityScore > 100)) {
    throw new AnalyticsApiError('format');
  }
  return data as unknown as AnalyticsSummary;
}

function validateExecutionsPage(data: unknown): AnalyticsPage<AnalyticsExecution> {
  // Planned contract: Spring Page<T> with content, number, size, totalElements, totalPages.
  if (!isObject(data) || !Array.isArray(data.content)
    || !nonNegativeNumber(data.number) || !nonNegativeNumber(data.size)
    || !nonNegativeNumber(data.totalElements) || !nonNegativeNumber(data.totalPages)
    || !data.content.every((item: unknown) => isObject(item)
      && typeof item.flightIata === 'string' && typeof item.flightDate === 'string'
      && typeof item.status === 'string'
      && (item.arrivalDelayMinutes === null || nonNegativeNumber(item.arrivalDelayMinutes))
      && (item.departureIata === null || typeof item.departureIata === 'string')
      && (item.arrivalIata === null || typeof item.arrivalIata === 'string')
      && (item.lastSyncedAt === null || typeof item.lastSyncedAt === 'string'))) {
    throw new AnalyticsApiError('format');
  }
  return data as unknown as AnalyticsPage<AnalyticsExecution>;
}
