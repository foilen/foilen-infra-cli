/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.util.List;

import com.foilen.infra.cli.commands.exec.model.ProgressionHook;
import com.foilen.infra.cli.commands.model.WebsitesAccessible;

public interface CheckService {

    List<WebsitesAccessible> checkWebsitesAccessible(ProgressionHook progressionHook);

    void listAllResourcesOnMachine(String machineName);

}