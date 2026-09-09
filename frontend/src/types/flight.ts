export interface FlightResponse {
  flightIata: string;
  status: 'ON_TIME' | 'DELAYED' | 'CANCELLED' | string;
  delayMinutes: number;
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
  degraded?: boolean;
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
  originIata?: string;
  originCity?: string;
  destIata?: string;
  destCity?: string;
}
