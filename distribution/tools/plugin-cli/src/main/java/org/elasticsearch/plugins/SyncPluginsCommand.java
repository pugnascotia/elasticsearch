/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugins;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.elasticsearch.Version;
import org.elasticsearch.cli.EnvironmentAwareCommand;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.env.Environment;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.elasticsearch.cli.Terminal.Verbosity.SILENT;
import static org.elasticsearch.cli.Terminal.Verbosity.VERBOSE;

/**
 * A command for the plugin cli to update the installed plugins from the plugin descriptor file.
 */
class SyncPluginsCommand extends EnvironmentAwareCommand {

    private final OptionSpec<Void> batchOption;
    private final OptionSpec<Void> purgeOption;
    private final OptionSpec<Void> dryOption;

    SyncPluginsCommand() {
        super("Synchronize the installed elasticsearch plugins from the plugin config file");
        this.batchOption = parser.acceptsAll(
            Arrays.asList("b", "batch"),
            "Enable batch mode - security permissions will be automatically granted to plugins"
        );
        this.purgeOption = parser.acceptsAll(Arrays.asList("p", "purge"), "Purge configuration files when removing plugins");
        this.dryOption = parser.acceptsAll(
            Arrays.asList("d", "dry-run"),
            "Report what actions would be taken but don't actually change anything"
        );
    }

    @Override
    protected void execute(Terminal terminal, OptionSet options, Environment env) throws Exception {
        final boolean isBatch = options.has(batchOption);
        final boolean isPurge = options.has(purgeOption);
        final boolean isDry = options.has(dryOption);

        if (Files.exists(env.pluginsFile()) == false) {
            throw new UserException(1, "Plugins directory missing: " + env.pluginsFile());
        }

        // 1. Parse descriptor file
        final PluginsManifest pluginsManifest = PluginsManifest.parseManifest(env);

        // 2. Get list of installed plugins
        final List<PluginInfo> existingPlugins = getExistingPlugins(env, terminal);

        // 3. Calculate changes
        final List<String> pluginsThatShouldExist = pluginsManifest.getPluginDescriptors().stream().map(PluginDescriptor::getId).collect(Collectors.toList());
        final List<String> pluginsThatActuallyExist = existingPlugins.stream().map(PluginInfo::getName).collect(Collectors.toList());

        final List<String> pluginsToInstall = difference(pluginsThatShouldExist, pluginsThatActuallyExist);
        final List<String> pluginsToRemove = difference(pluginsThatActuallyExist, pluginsThatShouldExist);

        printRequiredChanges(terminal, isDry, pluginsToRemove, pluginsToInstall);

        if (isDry) {
            return;
        }

        // 5. Remove any plugins that are not in the descriptor
        if (pluginsToRemove.isEmpty() == false) {
            final RemovePluginCommand removePluginCommand = new RemovePluginCommand();
            removePluginCommand.execute(terminal, env, pluginsToRemove, isPurge);
        }

        // 6. Add any plugins that are in the descriptor but missing from disk
        if (pluginsToInstall.isEmpty() == false) {
            final InstallPluginCommand installPluginCommand = new InstallPluginCommand();
            installPluginCommand.execute(terminal, pluginsToInstall, isBatch, env, pluginsManifest.getProxy());
        }
    }

    private List<PluginInfo> getExistingPlugins(Environment env, Terminal terminal) throws IOException {
        final List<PluginInfo> plugins = new ArrayList<>();

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(env.pluginsFile())) {
            for (Path pluginPath : paths) {
                PluginInfo info = PluginInfo.readFromProperties(env.pluginsFile().resolve(pluginPath));
                plugins.add(info);
                if (info.getElasticsearchVersion().equals(Version.CURRENT) == false) {
                    terminal.errorPrintln(
                        "WARNING: plugin ["
                            + info.getName()
                            + "] was built for Elasticsearch version "
                            + info.getElasticsearchVersion()
                            + " but version "
                            + Version.CURRENT
                            + " is required"
                    );
                }
            }
        }

        plugins.sort(Comparator.comparing(PluginInfo::getName));
        return plugins;
    }

    private static <T> List<T> difference(Collection<T> left, Collection<T> right) {
        return left.stream().filter(k -> right.contains(k) == false).collect(Collectors.toList());
    }

    private void printRequiredChanges(Terminal terminal, boolean isDry, List<String> pluginsToRemove, List<String> pluginsToInstall) {
        final Terminal.Verbosity verbosity = isDry ? SILENT : VERBOSE;

        if (pluginsToInstall.isEmpty() && pluginsToRemove.isEmpty()) {
            terminal.println(verbosity, "No plugins to install or remove.");
        } else {
            if (pluginsToRemove.isEmpty()) {
                terminal.println(verbosity, "No plugins to remove.");
            } else {
                terminal.println(verbosity, "The following plugins need to be removed:");
                terminal.println(verbosity, "");
                pluginsToRemove.forEach(p -> terminal.println(verbosity, "    " + p));
                terminal.println(verbosity, "");
            }

            if (pluginsToInstall.isEmpty()) {
                terminal.println(verbosity, "No plugins to install.");
            } else {
                terminal.println(verbosity, "The following plugins need to be installed:");
                terminal.println(verbosity, "");
                pluginsToInstall.forEach(p -> terminal.println(verbosity, "    " + p));
                terminal.println(verbosity, "");
            }
        }
    }
}
