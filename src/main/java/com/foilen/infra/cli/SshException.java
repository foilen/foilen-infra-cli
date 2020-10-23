/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli;

import java.util.Queue;

public class SshException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private Queue<String> lastErrorLines;

    public SshException(String message, Queue<String> lastErrorLines) {
        super(message);
        this.lastErrorLines = lastErrorLines;
    }

    public SshException(String message, Throwable cause, Queue<String> lastErrorLines) {
        super(message, cause);
        this.lastErrorLines = lastErrorLines;
    }

    public Queue<String> getLastErrorLines() {
        return lastErrorLines;
    }

}
