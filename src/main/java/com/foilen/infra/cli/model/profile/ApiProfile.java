/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.model.profile;

public class ApiProfile extends AbstractProfile implements ProfileHasCert, ProfileHasPassword {

    private String infraBaseUrl;
    private String apiUser;
    private String apiKey;
    private String sshCertificateFile;
    private String sshPassword;

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUser() {
        return apiUser;
    }

    public String getInfraBaseUrl() {
        return infraBaseUrl;
    }

    @Override
    public String getSshCertificateFile() {
        return sshCertificateFile;
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

    public void setSshCertificateFile(String sshCertificateFile) {
        this.sshCertificateFile = sshCertificateFile;
    }

    @Override
    public String getSshPassword() {
        return sshPassword;
    }

    public void setSshPassword(String sshPassword) {
        this.sshPassword = sshPassword;
    }
}
