/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands.model;

import java.util.ArrayList;
import java.util.List;

import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.SpaceConverterTools;
import com.foilen.smalltools.tools.TimeConverterTools;

public class BackupResults extends AbstractBasics {

    private boolean success = true;
    private boolean completed = false;

    private long totalExecutionTimeMs;
    private long totalFileSize;

    private List<BackupResult> results = new ArrayList<>();

    public BackupResult addResult(boolean success, String owner, String machineName, String name, long executionTimeMs, long fileSize) {
        totalExecutionTimeMs += executionTimeMs;
        totalFileSize += fileSize;

        if (!success) {
            this.success = false;
        }

        BackupResult br = new BackupResult(success, owner, machineName, name, executionTimeMs, fileSize);
        results.add(br);
        return br;
    }

    public List<BackupResult> getResults() {
        return results;
    }

    public long getTotalExecutionTimeMs() {
        return totalExecutionTimeMs;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        results.forEach(r -> sb.append(r).append("\n"));

        sb.append("\n");
        sb.append("\nWas a success: ").append(success);
        sb.append("\nIs completed: ").append(completed);
        sb.append("\nTotal execution time: ").append(TimeConverterTools.convertToText(totalExecutionTimeMs));
        sb.append("\nTotal size: ").append(SpaceConverterTools.convertToBiggestBUnit(totalFileSize));

        return sb.toString();
    }

}
