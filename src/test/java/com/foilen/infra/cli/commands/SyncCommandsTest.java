/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import org.junit.Assert;
import org.junit.Test;

public class SyncCommandsTest {

    @Test
    public void testTrimSlashes() {
        Assert.assertEquals("", SyncCommands.trimSlashes(null));
        Assert.assertEquals("", SyncCommands.trimSlashes("////"));
        Assert.assertEquals("sub", SyncCommands.trimSlashes("sub"));
        Assert.assertEquals("sub/1", SyncCommands.trimSlashes("sub/1"));
        Assert.assertEquals("sub/1", SyncCommands.trimSlashes("/sub/1/"));
        Assert.assertEquals("sub/1", SyncCommands.trimSlashes("///sub/1///"));
    }

}
