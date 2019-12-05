/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;

import com.foilen.infra.cli.commands.model.RawDnsEntry;
import com.foilen.smalltools.reflection.ReflectionTools;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;
import com.google.common.base.Strings;

@ShellComponent
public class DnsCommands extends AbstractBasics {

    private void addSubDomains(Collection<String> hostnames, String hostname, String... subs) {
        for (String sub : subs) {
            hostnames.add(sub + "." + hostname);
        }
    }

    @ShellMethod("Look the current DNS entries for a hostname with common sub-domains")
    public void dnsQuery( //
            String hostname, //
            @ShellOption(defaultValue = ShellOption.NULL) String moreSubDomains, //
            @ShellOption(defaultValue = ShellOption.NULL) String usingDnsServer //
    ) {

        // Hostnames to check
        Deque<String> hostnames = new LinkedList<>();
        hostnames.add(hostname);
        addSubDomains(hostnames, hostname, //
                "ns1", "ns2", "ns3", "ns4", // DNS
                "beta", "dev", "pre", "www", "w3", // Web
                "autodiscover", "email", "imap", "mail", "mx", "pop", "pop3", "smtp", // Emails
                "default._domainkey", "_amazonses", "mandrill._domainkey", "s1._domainkey", "s2._domainkey", // Mail keys
                "cpanel", "whm", // CPanel
                "ftp", "login", "gitlab", "phpmyadmin", "sso", "shop", "unifi", "unms", "webdisk", "webmail" // Common apps
        );
        if (moreSubDomains != null) {
            addSubDomains(hostnames, hostname, moreSubDomains.split(","));
        }

        // Get the list of DNS Types
        Map<Integer, String> typeById = new HashMap<>();
        for (Field field : ReflectionTools.allFields(Type.class)) {
            try {
                typeById.put(field.getInt(null), field.getName());
            } catch (Exception e) {
            }
        }

        // Check
        Set<String> visited = new HashSet<>();
        List<RawDnsEntry> dnsEntries = new ArrayList<>();
        while (!hostnames.isEmpty()) {
            String nextHostname = hostnames.pop();
            if (!visited.add(nextHostname)) {
                continue;
            }
            logger.info("Checking {}", nextHostname);

            for (int typeToLookup : Arrays.asList(Type.ANY, Type.MX)) {

                try {
                    Lookup lookup = new Lookup(nextHostname, typeToLookup);

                    // Use a specific name server
                    if (!Strings.isNullOrEmpty(usingDnsServer)) {
                        SimpleResolver resolver = new SimpleResolver(usingDnsServer);
                        lookup.setResolver(resolver);
                    }

                    // Get the records
                    Record[] records = lookup.run();
                    if (records == null) {
                        continue;
                    }
                    for (Record record : records) {

                        String typeName = typeById.get(record.getType());
                        RawDnsEntry dnsEntry = new RawDnsEntry() //
                                .setName(record.getName().toString(true)) //
                                .setType(typeName) //
                                .setTtl(record.getTTL()) //
                        ;
                        switch (record.getType()) {
                        case Type.A:
                            dnsEntry.setDetails(((ARecord) record).getAddress().getHostAddress());
                            dnsEntries.add(dnsEntry);
                            break;
                        case Type.AAAA:
                            dnsEntry.setDetails(((AAAARecord) record).getAddress().getHostAddress());
                            dnsEntries.add(dnsEntry);
                            break;
                        case Type.CNAME:
                            dnsEntry.setDetails(((CNAMERecord) record).getTarget().toString(true));
                            dnsEntries.add(dnsEntry);
                            if (dnsEntry.getDetails().endsWith(hostname)) {
                                hostnames.add(dnsEntry.getDetails());
                            }
                            break;
                        case Type.MX:
                            MXRecord mxRecord = (MXRecord) record;
                            dnsEntry.setDetails(mxRecord.getTarget().toString(true));
                            dnsEntry.setPriority(mxRecord.getPriority());
                            dnsEntries.add(dnsEntry);
                            if (dnsEntry.getDetails().endsWith(hostname)) {
                                hostnames.add(dnsEntry.getDetails());
                            }
                            break;
                        case Type.NS:
                            dnsEntry.setDetails(((NSRecord) record).getTarget().toString(true));
                            dnsEntries.add(dnsEntry);
                            break;
                        case Type.TXT:
                            ((List<?>) ((TXTRecord) record).getStrings()).forEach(it -> {
                                dnsEntry.setDetails(it.toString());
                                dnsEntries.add(JsonTools.clone(dnsEntry));
                            });
                            break;
                        case Type.SOA:
                            break;
                        default:
                            logger.error("Unknown type {} for {}", typeName, nextHostname);
                            continue;
                        }

                    }
                } catch (Exception e) {
                    logger.error("Problem checking {}", nextHostname, e);
                }
            }
        }

        Collections.sort(dnsEntries);
        System.out.println("\n\n---[" + hostname + "]---");
        dnsEntries.stream().sorted().distinct() //
                .forEach(it -> System.out.println(JsonTools.compactPrintWithoutNulls(it)));

    }

}
