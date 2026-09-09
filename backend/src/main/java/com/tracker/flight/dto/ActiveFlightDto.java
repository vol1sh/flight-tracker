package com.tracker.flight.dto;

import java.io.Serializable;

public class ActiveFlightDto implements Serializable {
    private String callsign;
    private String airline;
    private String originIata;
    private String originCity;
    private String destIata;
    private String destCity;

    public ActiveFlightDto() {}

    public ActiveFlightDto(String callsign, String airline, String originIata, String originCity, String destIata, String destCity) {
        this.callsign = callsign;
        this.airline = airline;
        this.originIata = originIata;
        this.originCity = originCity;
        this.destIata = destIata;
        this.destCity = destCity;
    }

    public String getCallsign() { return callsign; }
    public void setCallsign(String callsign) { this.callsign = callsign; }

    public String getAirline() { return airline; }
    public void setAirline(String airline) { this.airline = airline; }

    public String getOriginIata() { return originIata; }
    public void setOriginIata(String originIata) { this.originIata = originIata; }

    public String getOriginCity() { return originCity; }
    public void setOriginCity(String originCity) { this.originCity = originCity; }

    public String getDestIata() { return destIata; }
    public void setDestIata(String destIata) { this.destIata = destIata; }

    public String getDestCity() { return destCity; }
    public void setDestCity(String destCity) { this.destCity = destCity; }
}
