/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import org.springframework.stereotype.Component;

import com.foilen.smalltools.tools.AbstractBasics;

@Component
public class ConsoleDisplayService extends AbstractBasics implements DisplayService {

    @Override
    public void display() {
        System.out.println();
    }

    @Override
    public void display(String text) {
        System.out.println(text);
    }

}
