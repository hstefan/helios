package com.spotify.helios.agent;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;

import com.spotify.helios.common.ServiceParser;

import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Throwables.propagate;
import static net.sourceforge.argparse4j.impl.Arguments.SUPPRESS;
import static net.sourceforge.argparse4j.impl.Arguments.append;
import static net.sourceforge.argparse4j.impl.Arguments.storeTrue;

public class AgentParser extends ServiceParser {

  private final AgentConfig agentConfig;

  public AgentParser(final String... args) throws ArgumentParserException {
    super("helios-agent", "Spotify Helios Agent", args);

    final Namespace options = getNamespace();
    final String uriString = options.getString("docker");
    try {
      new URI(uriString);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Bad docker endpoint: " + uriString, e);
    }

    final String name = options.getString("name");

    final List<List<String>> env = options.getList("env");
    final Map<String, String> envVars = Maps.newHashMap();
    if (env != null) {
      for (final List<String> group : env) {
        for (final String s : group) {
          final String[] parts = s.split("=", 2);
          if (parts.length != 2) {
            throw new IllegalArgumentException("Bad environment variable: " + s);
          }
          envVars.put(parts[0], parts[1]);
        }
      }
    }

    agentConfig = new AgentConfig()
        .setName(name)
        .setZooKeeperConnectionString(options.getString("zk"))
        .setZooKeeperSessionTimeoutMillis(options.getInt("zk_session_timeout"))
        .setZooKeeperConnectionTimeoutMillis(options.getInt("zk_connection_timeout"))
        .setSite(options.getString("site"))
        .setMuninReporterPort(options.getInt("munin_port"))
        .setEnvVars(envVars)
        .setDockerEndpoint(options.getString("docker"))
        .setInhibitMetrics(Objects.equal(options.getBoolean("no_metrics"), true))
        .setRedirectToSyslog(options.getString("syslog_redirect_to"))
        .setStateDirectory(Paths.get(options.getString("state_dir")));
  }

  @Override
  protected void addArgs(final ArgumentParser parser) {
    parser.addArgument("--name")
        .setDefault(getHostName())
        .help("agent name");

    parser.addArgument("--state-dir")
        .setDefault(".")
        .help("Directory for persisting agent state locally.");

    parser.addArgument("--munin-port")
        .type(Integer.class)
        .setDefault(4952)
        .help("munin port (0 = disabled)");

    parser.addArgument("--docker")
        .setDefault("http://localhost:4160")
        .help("docker endpoint");

    parser.addArgument("--env")
        .action(append())
        .setDefault(new ArrayList<String>())
        .nargs("+")
        .help("Specify environment variables that will pass down to all containers");

    parser.addArgument("--syslog-redirect-to")
        .help("redirect container's stdout/stderr to syslog running at host:port");

    parser.addArgument("--no-metrics")
        .setDefault(SUPPRESS)
        .action(storeTrue())
        .help("Turn off all collection and reporting of metrics");
  }

  private static String getHostName() {
    return exec("uname -n").trim();
  }

  private static String exec(final String command) {
    try {
      final Process process = Runtime.getRuntime().exec(command);
      return CharStreams.toString(new InputStreamReader(process.getInputStream()));
    } catch (IOException e) {
      throw propagate(e);
    }
  }

  public AgentConfig getAgentConfig() {
    return agentConfig;
  }

}
