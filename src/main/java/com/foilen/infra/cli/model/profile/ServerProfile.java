/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.model.profile;

public class ServerProfile extends AbstractProfile implements ProfileHasCert, ProfileHasHostname, ProfileHasUser {

    private String hostname;
    private int sshPort = 22;
    private String username = "root";
    private String sshCertificateFile;

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public String getSshCertificateFile() {
        return sshCertificateFile;
    }

    public int getSshPort() {
        return sshPort;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setSshCertificateFile(String sshCertificateFile) {
        this.sshCertificateFile = sshCertificateFile;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public void setUsername(String username) {
        this.username = username;
    }

}
