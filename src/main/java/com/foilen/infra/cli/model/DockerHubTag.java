/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.foilen.smalltools.tools.AbstractBasics;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerHubTag extends AbstractBasics {

    private String name;
    @JsonProperty("last_updated")
    private String lastUpdated;

    public String getLastUpdated() {
        return lastUpdated;
    }

    public String getName() {
        return name;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setName(String name) {
        this.name = name;
    }

}
