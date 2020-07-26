/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands.model;

import com.foilen.smalltools.tools.AbstractBasics;
import com.google.common.collect.ComparisonChain;

public class WebsitesAccessible extends AbstractBasics implements Comparable<WebsitesAccessible> {

    private String url;
    private String websiteName;

    private int httpStatus;
    private String error;
    private boolean success;

    private long executionTimeMs;

    public WebsitesAccessible() {
    }

    public WebsitesAccessible(String url, String websiteName) {
        this.url = url;
        this.websiteName = websiteName;
    }

    @Override
    public int compareTo(WebsitesAccessible o) {
        return ComparisonChain.start() //
                .compareFalseFirst(success, o.success) //
                .compare(httpStatus, o.httpStatus) //
                .compare(executionTimeMs, o.executionTimeMs) //
                .compare(url, o.url) //
                .compare(websiteName, o.websiteName) //
                .result();
    }

    public String getError() {
        return error;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getUrl() {
        return url;
    }

    public String getWebsiteName() {
        return websiteName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setWebsiteName(String websiteName) {
        this.websiteName = websiteName;
    }

}
