/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.model;

import com.foilen.smalltools.tools.AbstractBasics;

public class OnlineFileDetails extends AbstractBasics {

    private String version;
    private String jarUrl;

    public String getJarUrl() {
        return jarUrl;
    }

    public String getVersion() {
        return version;
    }

    public OnlineFileDetails setJarUrl(String jarUrl) {
        this.jarUrl = jarUrl;
        return this;
    }

    public OnlineFileDetails setVersion(String version) {
        this.version = version;
        return this;
    }

}
