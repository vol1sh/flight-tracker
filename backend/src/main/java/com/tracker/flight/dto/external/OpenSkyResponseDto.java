package com.tracker.flight.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenSkyResponseDto {

    private Integer time;
    private List<List<Object>> states;

    public Integer getTime() {
        return time;
    }

    public void setTime(Integer time) {
        this.time = time;
    }

    public List<List<Object>> getStates() {
        return states;
    }

    public void setStates(List<List<Object>> states) {
        this.states = states;
    }
}
