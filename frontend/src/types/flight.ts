export interface FlightResponse {
  flightIata: string;
  departureAirport: string;
  arrivalAirport: string;
  scheduledDeparture: string;
  actualDeparture: string | null;
  scheduledArrival: string;
  actualArrival: string | null;
  status: string;
  delayMinutes: number;
  degraded?: boolean;
  isDegraded?: boolean;
  lastUpdated: string;
}

export interface FlightStatusLog {
  previousStatus: string | null;
  newStatus: string;
  delayMinutes: number;
  recordedAt: string;
}
