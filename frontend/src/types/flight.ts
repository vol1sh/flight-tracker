export interface FlightResponse {
  flightIata: string;
  departureAirport: string;
  departureCity?: string;
  departureAirportName?: string;
  arrivalAirport: string;
  arrivalCity?: string;
  arrivalAirportName?: string;
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

export interface ActiveFlight {
  callsign: string;
  airline: string;
  originIata: string;
  originCity: string;
  destIata: string;
  destCity: string;
}
