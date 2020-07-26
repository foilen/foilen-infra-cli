/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import org.junit.Assert;
import org.junit.Test;

public class UpdateCommandsTest {

    @Test
    public void testGetPluginNameFromUrl() {
        UpdateCommands updateCommands = new UpdateCommands();
        Assert.assertEquals("foilen-infra-resource-composableapplication", updateCommands
                .getPluginNameFromUrl("https://dl.bintray.com/foilen/maven/com/foilen/foilen-infra-resource-composableapplication/0.2.1/foilen-infra-resource-composableapplication-0.2.1.jar"));
    }

    @Test
    public void testGetVersionFromUrl() {
        UpdateCommands updateCommands = new UpdateCommands();
        Assert.assertEquals("0.2.1", updateCommands
                .getVersionFromUrl("https://dl.bintray.com/foilen/maven/com/foilen/foilen-infra-resource-composableapplication/0.2.1/foilen-infra-resource-composableapplication-0.2.1.jar"));
    }

}
