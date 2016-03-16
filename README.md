# sbt-conductr #

[![Build Status](https://api.travis-ci.org/sbt/sbt-conductr.png?branch=master)](https://travis-ci.org/sbt/sbt-conductr)

sbt-conductr is a sbt plugin that provides commands to communicate with ConductR within a sbt session. It can
also be used in conjunction with [sbt-bundle](https://github.com/sbt/sbt-bundle#conductr-bundle-plugin) 
to deploy your application or service to ConductR without leaving the comforts of sbt.

## Prerequisite

sbt-conductr uses the [conductr-cli](https://github.com/typesafehub/conductr-cli) to communicate with ConductR.
Follow the instructions on [http://conductr.lightbend.com/docs/1.1.x/CLI](http://conductr.lightbend.com/docs/1.1.x/CLI) to install the CLI.

## Usage

sbt-conductr is enabled by simply declaring its dependency:

```scala
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
addSbtPlugin("com.typesafe.conductr" % "sbt-conductr" % "1.5.1")
```

The plugin has no requirements for other plugins to be enabled.

To communicate with the ConductR cluster use the `conduct` command, e.g.

```
conduct info
```

This will obtain information on the ConductR cluster.

The IP address of the ConductR host is automatically retrieved and used if:
- ConductR is running on the same host inside a docker container. The ConductR address is set to `http://{docker-host-ip}:9005`.
- ConductR is running on the same host. The ConductR address is set to `http://{hostname}:9005`.

In other scenarios it is necessary to set the address to the ConductR server manually. You can do this by using the `--ip` and `--port` options of the `conduct command, e.g.

```
conduct info --ip 192.168.59.103 --port 9999
```

If you have [sbt-bundle](https://github.com/sbt/sbt-bundle#conductr-bundle-plugin) enabled e.g.:

```scala
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
```

You can then produce a bundle by typing:

```bash
bundle:dist
```

...and then load the bundle by typing:

```bash
conduct load <HIT THE TAB KEY AND THEN RETURN>
```

Using the tab completion feature of sbt will produce a URI representing the location of the last distribution
produced by the native packager.

Hitting return will cause the bundle to be uploaded. On successfully uploading the bundle the plugin will report
the `BundleId` to use for subsequent commands on that bundle.

#### Configuration

When loading a bundle you can also load configuration if there is any e.g.:

```bash
configuration:dist
```

...and then hit the tab key after specifying the bundle for `conduct load`.

### Retrieve logs and events

The log messages and events of a particular bundle can be displayed with the commands:

```bash
conduct logs my-bundle
conduct events my-bundle
```

Make sure that your logging infrastructure is up an running. Otherwise the command will timeout: 

```
[trace] Stack trace suppressed: run last conductr-service-lookup/*:conduct for the full output.
[error] (conductr-service-lookup/*:conduct) java.util.concurrent.TimeoutException: Futures timed out after [5 seconds]
[error] Total time: 5 s, completed Sep 28, 2015 11:40:47 AM
```

With [sbt-conductr-sandbox](https://github.com/typesafehub/sbt-conductr-sandbox) you can start the default logging infrastructure during ConductR cluster startup easily:

```bash
sandbox run --withFeatures logging
conduct logs my-bundle
```

Give the logging infrastructure enough time to start before entering the `logs` or `events` command.

### Settings

The following `sbt-conductr` commands are available:

Property               | Description
-----------------------|------------
conduct help           | Get usage information of the conduct command
conduct info           | Gain information on the cluster
conduct load           | Loads a bundle and an optional configuration to the ConductR
conduct run            | Runs a bundle given a bundle id with an optional absolute scale value specified with --scale
conduct stop           | Stops all executions of a bundle given a bundle id
conduct unload         | Unloads a bundle entirely (requires that the bundle has stopped executing everywhere)
conduct logs           | Retrieves log messages of a given bundle
conduct events         | Retrieves events of a given bundle


&copy; Lightbend Inc., 2014-2016
