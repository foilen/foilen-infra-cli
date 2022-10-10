/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands.exec;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;

import com.foilen.infra.cli.commands.exec.model.ProgressionHook;
import com.foilen.infra.cli.commands.model.WebsitesAccessible;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.TimeExecutionTools;

public class CheckWebsiteAccessible extends AbstractBasics implements Runnable {

    private static final int TIMEOUT_MS = 30000;

    private ProgressionHook progressionHook;
    private WebsitesAccessible websitesAccessible;

    public CheckWebsiteAccessible(ProgressionHook progressionHook, WebsitesAccessible websitesAccessible) {
        this.progressionHook = progressionHook;
        this.websitesAccessible = websitesAccessible;
    }

    @Override
    public void run() {

        RequestConfig requestConfig = RequestConfig.custom() //
                .setConnectTimeout(TIMEOUT_MS) //
                .setSocketTimeout(20000) //
                .setConnectionRequestTimeout(20000) //
                .setRedirectsEnabled(false) //
                .build();

        HttpClient httpClient = HttpClientBuilder.create() //
                .setDefaultRequestConfig(requestConfig) //
                .build();

        websitesAccessible.setExecutionTimeMs(TimeExecutionTools.measureInMs(() -> {

            HttpUriRequest request = new HttpGet(websitesAccessible.getUrl());
            try {
                progressionHook.begin();
                HttpResponse response = httpClient.execute(request);
                StatusLine statusLine = response.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                websitesAccessible.setHttpStatus(statusCode);
                websitesAccessible.setError(statusLine.getReasonPhrase());

                // Check code for success
                switch (statusCode) {
                case 200:
                case 301:
                case 302:
                case 401:
                    websitesAccessible.setSuccess(true);
                    break;
                default:
                }
            } catch (Exception e) {
                websitesAccessible.setError(e.getClass().getSimpleName() + " : " + e.getMessage());
            }

            progressionHook.done();

        }));

    }

}
