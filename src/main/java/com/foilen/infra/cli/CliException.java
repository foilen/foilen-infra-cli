/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli;

import com.foilen.smalltools.restapi.model.ApiError;

public class CliException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CliException() {
        super();
    }

    public CliException(ApiError error) {
        super(error.getTimestamp() + " " + error.getUniqueId() + " " + error.getMessage());
    }

    public CliException(String message) {
        super(message);
    }

    public CliException(String message, Throwable cause) {
        super(message, cause);
    }

}
