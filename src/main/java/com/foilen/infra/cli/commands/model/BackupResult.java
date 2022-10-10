/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands.model;

import java.util.ArrayList;
import java.util.List;

import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.SpaceConverterTools;
import com.foilen.smalltools.tools.TimeConverterTools;
import com.google.common.base.Joiner;

public class BackupResult extends AbstractBasics {

    private boolean success;

    private String owner;
    private String machineName;
    private String unixUserName;
    private long executionTimeMs;
    private long fileSize;

    private List<String> errors = new ArrayList<>();

    public BackupResult() {
    }

    public BackupResult(boolean success, String owner, String machineName, String unixUserName, long executionTimeMs, long fileSize) {
        this.success = success;
        this.owner = owner;
        this.machineName = machineName;
        this.unixUserName = unixUserName;
        this.executionTimeMs = executionTimeMs;
        this.fileSize = fileSize;
    }

    public void addError(String error) {
        errors.add(error);
    }

    public List<String> getErrors() {
        return errors;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getMachineName() {
        return machineName;
    }

    public String getOwner() {
        return owner;
    }

    public String getUnixUserName() {
        return unixUserName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setUnixUserName(String unixUserName) {
        this.unixUserName = unixUserName;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (success) {
            sb.append("[OK] ");
        } else {
            sb.append("[ERROR] ");
        }

        sb.append(Joiner.on(" | ").join(owner, machineName, unixUserName, //
                TimeConverterTools.convertToTextFromMs(executionTimeMs), SpaceConverterTools.convertToBiggestBUnit(fileSize)));
        errors.forEach(e -> sb.append("\n\t" + e));

        return sb.toString();
    }

}
