export interface FlightResponse {
  flightIata: string;
  flightDate: string;
  status: 'SCHEDULED' | 'ACTIVE' | 'ON_TIME' | 'DELAYED' | 'LANDED' | 'CANCELLED' | string;
  delayMinutes: number | null;
  departureAirport: string;
  departureCity?: string;
  departureAirportName?: string;
  arrivalAirport: string;
  arrivalCity?: string;
  arrivalAirportName?: string;
  scheduledDeparture: string | null;
  actualDeparture: string | null;
  scheduledArrival: string | null;
  actualArrival: string | null;
  lastUpdated: string | null;
  lastSyncedAt?: string | null;
  dataFresh: boolean;
  isDegraded?: boolean;
}

export interface FlightStatusLog {
  recordedAt: string;
  previousStatus: string | null;
  newStatus: string;
  delayMinutes: number;
}

export interface ActiveFlight {
  callsign: string;
  flightDate?: string;
  originIata?: string;
  originCity?: string;
  destIata?: string;
  destCity?: string;
}
