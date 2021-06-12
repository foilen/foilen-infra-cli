/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

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
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;

import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.commands.model.RawDnsEntry;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.resource.dns.DnsEntry;
import com.foilen.smalltools.listscomparator.ListComparatorHandler;
import com.foilen.smalltools.listscomparator.ListsComparator;
import com.foilen.smalltools.reflection.ReflectionTools;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.StringTools;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

@ShellComponent
public class DnsCommands extends AbstractBasics {

    @Autowired
    private ExceptionService exceptionService;

    @Autowired
    private ProfileService profileService;

    private void addSubDomains(Collection<String> hostnames, String hostname, String... subs) {
        for (String sub : subs) {
            hostnames.add(sub + "." + hostname);
        }
    }

    private String asString(RawDnsEntry entry) {
        return Joiner.on(" | ").skipNulls().join(entry.getName(), entry.getType(), entry.getDetails(), entry.getPriority(), entry.getWeight(), entry.getPort());
    }

    @ShellMethod("Look the current DNS entries for a hostname with common sub-domains and compare with what is in the target profile (ignoring ttl and MX priority")
    public void dnsCompareWithTarget( //
            String hostname, //
            @ShellOption(defaultValue = ShellOption.NULL) String moreSubDomains, //
            @ShellOption(defaultValue = ShellOption.NULL) String usingDnsServer //
    ) {

        // get source
        List<String> sourceDnsEntries = listRawDnsEntries(hostname, moreSubDomains, usingDnsServer).stream() //
                .filter(rawDnsEntry -> !StringTools.safeEquals(rawDnsEntry.getType(), "NS")) //
                .map(rawDnsEntry -> asString(rawDnsEntry)) //
                .sorted().distinct() //
                .collect(Collectors.toList());

        // Get target
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(DnsEntry.RESOURCE_TYPE));
        exceptionService.displayResultAndThrow(resourceBuckets, "Get DNS List");
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }

        List<String> targetDnsEntries = resourceBuckets.getItems().stream() //
                .map(resourceBucket -> JsonTools.clone(resourceBucket.getResourceDetails().getResource(), DnsEntry.class)) //
                .filter(dnsEntry -> dnsEntry.getName().endsWith(hostname)) //
                .map(dnsEntry -> {
                    RawDnsEntry rawDnsEntry = new RawDnsEntry() //
                            .setName(dnsEntry.getName()) //
                            .setType(dnsEntry.getType().name()) //
                            .setDetails(dnsEntry.getDetails()) //
                            .setTtl(300) //
                    ;
                    switch (dnsEntry.getType()) {
                    case MX:
                        rawDnsEntry.setPriority(dnsEntry.getPriority());
                        break;
                    case SRV:
                        rawDnsEntry.setPriority(dnsEntry.getPriority());
                        rawDnsEntry.setWeight(dnsEntry.getWeight());
                        rawDnsEntry.setPort(dnsEntry.getPort());
                        break;
                    default:
                    }
                    return rawDnsEntry;
                }) //
                .map(rawDnsEntry -> asString(rawDnsEntry)) //
                .sorted().distinct() //
                .collect(Collectors.toList());

        // Compare
        ListsComparator.compareLists(sourceDnsEntries, targetDnsEntries, new ListComparatorHandler<String, String>() {

            @Override
            public void both(String left, String right) {
            }

            @Override
            public void leftOnly(String left) {
                System.out.println("ADD\t" + left);
            }

            @Override
            public void rightOnly(String right) {
                System.out.println("REMOVE\t" + right);
            }
        });

    }

    @ShellMethod("Look the current DNS entries for a hostname with common sub-domains")
    public void dnsQuery( //
            String hostname, //
            @ShellOption(defaultValue = ShellOption.NULL) String moreSubDomains, //
            @ShellOption(defaultValue = ShellOption.NULL) String usingDnsServer //
    ) {

        List<RawDnsEntry> dnsEntries = listRawDnsEntries(hostname, moreSubDomains, usingDnsServer);
        System.out.println("\n\n---[" + hostname + "]---");
        dnsEntries.stream().sorted().distinct() //
                .forEach(it -> System.out.println(JsonTools.compactPrintWithoutNulls(it)));

    }

    @ShellMethodAvailability
    public Availability isAvailable() {

        if (profileService.getTarget() == null) {
            return Availability.unavailable("you did not specify a target profile");
        }

        if (profileService.getTarget() instanceof ApiProfile) {
            return Availability.available();
        }

        return Availability.unavailable("the target profile is not of API type");
    }

    private List<RawDnsEntry> listRawDnsEntries(String hostname, String moreSubDomains, String usingDnsServer) {
        // Hostnames to check
        Deque<String> hostnames = new LinkedList<>();
        hostnames.add(hostname);
        addSubDomains(hostnames, hostname, //
                "ns1", "ns2", "ns3", "ns4", // DNS
                "beta", "dev", "pre", "www", "w3", // Web
                "_imap._tcp", "_imaps._tcp", "_submission._tcp", "_pop._tcp", "_pop3._tcp", "autodiscover", "email", "imap", "mail", "mx", "pop", "pop3", "smtp", // Emails
                "default._domainkey", "_amazonses", "mandrill._domainkey", "s1._domainkey", "s2._domainkey", // Mail keys
                "_caldavs._tcp", // CalDav
                "cpanel", "whm", // CPanel
                "_sip._tls", "_sipfederationtls._tcp", "sip", // SIP
                "_jabber._tcp", "_ldap._tcp", "_xmpp-client._tcp", "enterpriseenrollment", "enterpriseregistration", "ftp", "login", "lyncdiscover", "lyncdiscoverinternal", "gitlab", "phpmyadmin",
                "sso", "shop", "unifi", "unms", "webdisk", "webmail" // Common apps
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
                        case Type.SRV:
                            SRVRecord srvRecord = (SRVRecord) record;
                            dnsEntry.setDetails(srvRecord.getTarget().toString(true));
                            dnsEntry.setPriority(srvRecord.getPriority());
                            dnsEntry.setWeight(srvRecord.getWeight());
                            dnsEntry.setPort(srvRecord.getPort());
                            dnsEntries.add(dnsEntry);
                            if (dnsEntry.getDetails().endsWith(hostname)) {
                                hostnames.add(dnsEntry.getDetails());
                            }
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
        return dnsEntries;
    }

}
