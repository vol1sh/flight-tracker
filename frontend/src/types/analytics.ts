export interface AnalyticsScope {
  type: 'FLIGHT' | 'ROUTE';
  flightIata: string | null;
  departureIata: string | null;
  arrivalIata: string | null;
}

export interface AnalyticsSummary {
  scope: AnalyticsScope;
  period: { from: string; to: string };
  sampleSize: number;
  landedCount: number;
  cancelledCount: number;
  knownArrivalDelayCount: number;
  averageDelayMinutes: number | null;
  onTimeRate: number | null;
  cancellationRate: number | null;
  reliabilityScore: number | null;
  dataSufficient: boolean;
  message: string | null;
  dataSource: 'POSTGRESQL';
  lastUpdatedAt: string | null;
}

export type AnalyticsFilter =
  | { type: 'FLIGHT'; flightIata: string; from: string; to: string }
  | { type: 'ROUTE'; departure: string; arrival: string; from: string; to: string };

export interface AnalyticsExecution {
  flightIata: string;
  flightDate: string;
  departureIata: string | null;
  arrivalIata: string | null;
  status: 'LANDED' | 'CANCELLED' | 'DELAYED' | 'SCHEDULED' | 'ACTIVE' | string;
  arrivalDelayMinutes: number | null;
  lastSyncedAt: string | null;
}

export interface AnalyticsPage<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
