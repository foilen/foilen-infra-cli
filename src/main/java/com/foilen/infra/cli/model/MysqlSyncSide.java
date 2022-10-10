/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.model;

import com.foilen.smalltools.tools.AbstractBasics;

public class MysqlSyncSide extends AbstractBasics {

    private String machineHost;
    private String machineUsername = "root";
    private String machinePassword;
    private String machineCert;

    private String dbHost = "127.0.0.1";
    private int dbPort = 3306;
    private String dbUsername;
    private String dbPassword;

    public String getDbHost() {
        return dbHost;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbUsername() {
        return dbUsername;
    }

    public String getMachineCert() {
        return machineCert;
    }

    public String getMachineHost() {
        return machineHost;
    }

    public String getMachinePassword() {
        return machinePassword;
    }

    public String getMachineUsername() {
        return machineUsername;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public void setDbPort(int dbPort) {
        this.dbPort = dbPort;
    }

    public void setDbUsername(String dbUsername) {
        this.dbUsername = dbUsername;
    }

    public void setMachineCert(String machineCert) {
        this.machineCert = machineCert;
    }

    public void setMachineHost(String machineHost) {
        this.machineHost = machineHost;
    }

    public void setMachinePassword(String machinePassword) {
        this.machinePassword = machinePassword;
    }

    public void setMachineUsername(String machineUsername) {
        this.machineUsername = machineUsername;
    }

}
