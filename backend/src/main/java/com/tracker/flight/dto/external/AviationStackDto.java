package com.tracker.flight.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AviationStackDto {

    private List<FlightItem> data;

    // AviationStack иногда возвращает HTTP 200 с телом {"error": {...}} вместо данных.
    // Это нужно уметь отличать от "пустого, но успешного" результата.
    private ApiError error;

    public List<FlightItem> getData() { return data; }
    public void setData(List<FlightItem> data) { this.data = data; }
    public ApiError getError() { return error; }
    public void setError(ApiError error) { this.error = error; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiError {
        private String code;
        private String message;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightItem {
        @JsonProperty("flight_date")
        private LocalDate flightDate;
        @JsonProperty("flight_status")
        private String flightStatus;
        private AirportInfo departure;
        private AirportInfo arrival;
        private AirlineInfo airline;
        private FlightDetail flight;

        public LocalDate getFlightDate() { return flightDate; }
        public void setFlightDate(LocalDate flightDate) { this.flightDate = flightDate; }
        public String getFlightStatus() { return flightStatus; }
        public void setFlightStatus(String flightStatus) { this.flightStatus = flightStatus; }
        public AirportInfo getDeparture() { return departure; }
        public void setDeparture(AirportInfo departure) { this.departure = departure; }
        public AirportInfo getArrival() { return arrival; }
        public void setArrival(AirportInfo arrival) { this.arrival = arrival; }
        public AirlineInfo getAirline() { return airline; }
        public void setAirline(AirlineInfo airline) { this.airline = airline; }
        public FlightDetail getFlight() { return flight; }
        public void setFlight(FlightDetail flight) { this.flight = flight; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirlineInfo {
        private String name;
        private String iata;
        private String icao;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIata() { return iata; }
        public void setIata(String iata) { this.iata = iata; }
        public String getIcao() { return icao; }
        public void setIcao(String icao) { this.icao = icao; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlightDetail {
        private String iata;
        private String icao;
        private String number;

        public String getIata() { return iata; }
        public void setIata(String iata) { this.iata = iata; }
        public String getIcao() { return icao; }
        public void setIcao(String icao) { this.icao = icao; }
        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AirportInfo {
        private String airport;
        private String timezone;
        private String iata;
        private String icao;
        private Integer delay;
        private String scheduled;
        private String actual;
        private String estimated;

        public String getAirport() { return airport; }
        public void setAirport(String airport) { this.airport = airport; }
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        public String getIata() { return iata; }
        public void setIata(String iata) { this.iata = iata; }
        public String getIcao() { return icao; }
        public void setIcao(String icao) { this.icao = icao; }
        public Integer getDelay() { return delay; }
        public void setDelay(Integer delay) { this.delay = delay; }
        public String getScheduled() { return scheduled; }
        public void setScheduled(String scheduled) { this.scheduled = scheduled; }
        public String getActual() { return actual; }
        public void setActual(String actual) { this.actual = actual; }
        public String getEstimated() { return estimated; }
        public void setEstimated(String estimated) { this.estimated = estimated; }
    }
}
