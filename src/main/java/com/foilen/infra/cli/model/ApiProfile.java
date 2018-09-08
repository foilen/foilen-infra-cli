/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2018 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.model;

public class ApiProfile extends AbstractProfile {

    private String infraBaseUrl;
    private String apiUser;
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUser() {
        return apiUser;
    }

    public String getInfraBaseUrl() {
        return infraBaseUrl;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setApiUser(String apiUser) {
        this.apiUser = apiUser;
    }

    public void setInfraBaseUrl(String infraBaseUrl) {
        this.infraBaseUrl = infraBaseUrl;
    }

}
