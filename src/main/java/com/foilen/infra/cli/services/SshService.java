/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2018 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import org.springframework.stereotype.Component;

import com.foilen.infra.cli.CliException;
import com.foilen.smalltools.jsch.JSchTools;
import com.foilen.smalltools.jsch.SshLogin;
import com.foilen.smalltools.tools.ThreadTools;

@Component
public class SshService {

    public void waitCanLogin(String hostname, String username, String password, int timeoutSeconds) {
        long maxTime = System.currentTimeMillis() + timeoutSeconds * 1000L;

        SshLogin sshLogin = new SshLogin(hostname, username).withPassword(password).autoApproveHostKey();
        while (!JSchTools.canLogin(sshLogin)) {

            if (System.currentTimeMillis() >= maxTime) {
                throw new CliException("Could not SSH to " + hostname + " with user " + username + " with a password");
            }

            // Wait
            System.out.print(".");
            ThreadTools.sleep(10000);
        }
        System.out.println();

    }

}
