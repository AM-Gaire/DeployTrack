package com.deploytrack.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Environment {
    @JsonProperty("dev") DEV,
    @JsonProperty("staging") STAGING,
    @JsonProperty("production") PRODUCTION
}
