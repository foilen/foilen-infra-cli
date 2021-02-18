/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

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
        Assert.assertEquals("foilen-infra-plugins-core",
                updateCommands.getPluginNameFromUrl("https://repo1.maven.org/maven2/com/foilen/foilen-infra-plugins-core/0.19.1/foilen-infra-plugins-core-0.19.1.jar"));
    }

    @Test
    public void testGetVersionFromUrl() {
        UpdateCommands updateCommands = new UpdateCommands();
        Assert.assertEquals("0.19.1", updateCommands.getVersionFromUrl("https://repo1.maven.org/maven2/com/foilen/foilen-infra-plugins-core/0.19.1/foilen-infra-plugins-core-0.19.1.jar"));
    }

}
