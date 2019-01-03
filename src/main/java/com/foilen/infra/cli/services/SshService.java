/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import org.springframework.stereotype.Component;

import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.MysqlSyncSide;
import com.foilen.smalltools.jsch.JSchTools;
import com.foilen.smalltools.jsch.SshLogin;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.ThreadTools;
import com.google.common.base.Strings;

@Component
public class SshService extends AbstractBasics {

    public JSchTools connect(MysqlSyncSide side) {
        SshLogin sshLogin = new SshLogin(side.getMachineHost(), side.getMachineUsername()).autoApproveHostKey();
        if (!Strings.isNullOrEmpty(side.getMachineCert())) {
            sshLogin.withPrivateKey(side.getMachineCert());
        } else if (!Strings.isNullOrEmpty(side.getMachinePassword())) {
            sshLogin.withPassword(side.getMachinePassword());
        } else {
            throw new CliException("Machine does not have a password or certificate to connect to it");
        }
        JSchTools jsch = new JSchTools().login(sshLogin);
        return jsch;
    }

    public void waitCanLogin(String hostname, String username, String password, int timeoutSeconds) {
        long maxTime = System.currentTimeMillis() + timeoutSeconds * 1000L;

        SshLogin sshLogin = new SshLogin(hostname, username).withPassword(password).autoApproveHostKey();
        while (!JSchTools.canLogin(sshLogin)) {

            if (System.currentTimeMillis() >= maxTime) {
                throw new CliException("Could not SSH to " + hostname + " with user " + username + " with a password");
            }

            // Wait
            logger.info("Waiting for password to propagate. Retry in 10 seconds");
            ThreadTools.sleep(10000);
        }
        System.out.println();

    }

}
