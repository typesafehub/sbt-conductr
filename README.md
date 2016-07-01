# sbt-conductr #

[![GitHub version](https://img.shields.io/badge/version-2.1.5-blue.svg)](https://github.com/typesafehub/sbt-conductr/releases)
[![Build Status](https://api.travis-ci.org/typesafehub/sbt-conductr.png?branch=master)](https://travis-ci.org/typesafehub/sbt-conductr)

sbt-conductr is a sbt plugin that provides commands in sbt to:
 
* Produce a ConductR bundle
* Start and stop a local ConductR cluster
* Manage a ConductR cluster within a sbt session
 
## Table of contents

* [Prerequisite](#prerequisite)
* [Setup](#setup)
* [Plugin Overview](#plugin-overview)
* [Command Overview](#command-overview)
* [ConductR Plugin](#conductr-plugin)
* [Bundle Plugins](#bundle-plugins)

## Prerequisite

* [Docker](https://www.docker.com/)
* [conductr-cli](http://conductr.lightbend.com/docs/1.1.x/CLI)

Docker is required to run the ConductR cluster as if it were running on a number of machines in your network. You won't need to understand much about Docker for ConductR other than installing it as described in its "Get Started" section. If you are on Windows or Mac then you will become familiar with `docker-machine` which is a utility that controls a virtual machine for the purposes of running Docker.

The conductr-cli is used to manage the ConductR cluster.

## Setup

Add sbt-conductr to your `project/plugins.sbt`:

```scala
addSbtPlugin("com.lightbend.conductr" % "sbt-conductr" % "2.1.5")
```

## Plugin Overview

sbt-conductr contains several sbt auto plugins. The following table provides an overview of the auto plugins and when they get triggered.

| Plugin                | Description                                                                    | Scope   | Trigger
|-----------------------|--------------------------------------------------------------------------------|---------|--------- 
| ConductrPlugin        | Uses the conductr-cli commands to manage a ConductR cluster                    | Global  | Always enabled
| BundlePlugin          | Produce a bundle and bundle configuration for a JavaAppPackaing application.   | Project | JavaAppPackaging
| PlayBundlePlugin      | Produce a bundle and bundle configuration for a Play application.              | Project | Play && BundlePlugin
| LagomPlayBundlePlugin | Produce a bundle and bundle configuration for a Play application inside a Lagom project. | Project | LagomPlay && BundlePlugin
| LagomBundlePlugin     | Produce a bundle and bundle configuration for a Lagom service.                 | Project | LagomJava && BundlePlugin
| LagomConductrPlugin   | Adds Lagom concerns to ConductrPlugin                                          | Global  | ConductrPlugin && LagomBundlePlugin

The `ConductRPlugin` is enabled as soon as `sbt-conductr` has been added to the project. The `BundlePlugin` is triggered for each project that enables a native packager plugin in the `build.sbt`, e.g.:

```scala
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
```

In the context of Play or Lagom you should enable the following plugins to trigger the respective bundle plugin:

| Project            | Description                                                                         |
|--------------------|-------------------------------------------------------------------------------------|
| Lagom Java         | `lazy val myService = (project in file(".")).enablePlugins(LagomJava)`              |
| Play Java in Lagom | `lazy val myService = (project in file(".")).enablePlugins(LagomPlay)`              |
| Play Scala 2.4+    | `lazy val root = (project in file(".")).enablePlugins(PlayScala)`                   |
| Play Scala 2.3     | `lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, PlayScala)` |
| Play Java 2.4+     | `lazy val root = (project in file(".")).enablePlugins(PlayJava)`                    |
| Play Java 2.3      | `lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, PlayJava)`  |

## Command Overview

The following `sbt-conductr` commands are available:

Property                     | Description
-----------------------------|------------
bundle:dist                  | Produce a ConductR bundle for all projects that have the native packager enabled
configuration:dist           | Produce a bundle configuration for all projects that have the native packager enabled
cassandra:configuration:dist | Produce one cassandra bundle configuration in the root target directory
sandbox help                 | Get usage information of the sandbox command
sandbox run                  | Start a local ConductR sandbox
sandbox stop                 | Stop the local ConductR sandbox
conduct help                 | Get usage information of the conduct command
conduct info                 | Gain information on the cluster
conduct load                 | Loads a bundle and an optional configuration to the ConductR
conduct run                  | Runs a bundle given a bundle id with an optional absolute scale value specified with --scale
conduct stop                 | Stops all executions of a bundle given a bundle id
conduct unload               | Unloads a bundle entirely (requires that the bundle has stopped executing everywhere)
conduct logs                 | Retrieves log messages of a given bundle
conduct events               | Retrieves events of a given bundle
install                      | Generates an installation script and then installs all of your projects to the local ConductR sandbox (expected to be running)

Each `sandbox` and `conduct` sub command has a help page particular for the sub command, e.g. `conduct run --help`.

## ConductR Plugin 

With the ConductR plugin it is possible to execute the [conductr-cli](https://github.com/typesafehub/conductr-cli) commands within the sbt session. The [Command Overview](#command-overview) section lists down all available `sandbox` and `conduct` commands.

### Starting cluster

To start a three-node ConductR cluster on your local machine use:

```console
sandbox run <CONDUCTR_VERSION> --nr-of-containers 3
```

Visit the [ConductR Developer page](https://www.lightbend.com/product/conductr/developer) to pick up the latest ConductR version from the section **Quick Configuration**.

To get an overview of all available `sandbox run` options use:

```
sandbox run --help
```

#### Features

The sandbox contains handy features which can be optionally enabled during startup by specifying the `--feature` option, e.g.:

```console
sandbox run <CONDUCTR_VERSION> --feature visualization
```

The following features are available:

| Name          | Description                                                                         |
|---------------|-------------------------------------------------------------------------------------|
| visualization | Provides a web interface to visualize the ConductR cluster together with the deployed running bundles. |
| logging       | Out-of-the-box the sandbox starts a simple logging service called `eslite`. This service is automatically enabled without specifying the `logging` feature. This in-memory logging service only captures the log messages within one node. In case you want to retrieve log messages from multiple nodes use the `logging` feature. This will start an elasticsearch and kibana bundle. The elasticsearch bundle will capture the stdout and sterr output of your bundles. To display the log messages use either the `conduct logs` command or the Kibana UI on port 5601. Make sure that your VM has sufficient memory when using this feature. |
| monitoring    | Enables Lightbend Monitoring for your bundles                   |

### Stopping cluster

To stop the ConductR sandbox use:

```
sandbox stop
```

### Installing your project

The `install` command will introspect your project and its sub-projects and then load and run everything in ConductR at once. The local sandbox is expected to be running and it will first be restarted to ensure that it is in a clean state.

### Generating an installation script

Just like the `install` command, the `generateInstallationScript` command also introspects your project but then writes what is required to load and run everything to a script. The command will output the location of the generated script once done. You are encouraged to copy this script and use it as the basis of installing your project for production deployments.

You can run the script as many times as you need; ConductR load and run commands are idempotent thus allowing you to conveniently load and run any individual components that have stopped for some reason.

### Retrieving bundle state

To check the status of your bundles use:

```console
conduct info
```

This will obtain information on the ConductR cluster.

### Loading bundles

To load a bundle to the ConductR cluster use:

```
conduct load <HIT THE TAB KEY AND THEN RETURN>
```

Using the tab completion feature of sbt will produce a URI representing the location of the last produced bundle within the sbt project. In the context of a multi sbt project it is possible that multiple bundles, each per sub project, has been created. In order to use the tab completion it is then necessary to first switch to the sub project and then use `conduct load`.

### Running bundles

To start a bundle in the cluster use:

```
conduct run BUNDLE_NAME
```

This will start the bundle on one instance. To scale it to several instances use the `--scale` option:

```
conduct run --scale 3 BUNDLE_NAME
```

As the bundle name you can either use the bundle id or bundle name. Also the name specified doesn't need to exactly match to the name of the bundle. The specified name only needs to be unqiue within the ConductR cluster. So in case you want to run the bundle `this-is-a-very-long-bundle-name` you can just type:

```
conduct run t
```

If multiple bundles with a starting `t` exist then the command is aborted and an error message is displayed.

To get an overview of all available `conduct run` options use:

```
sandbox run --help
```

### Stopping bundles

Use the `stop` command to stop a bundle:

```console
conduct stop
```

### Retrieve log messages of a bundle

To retrieve log messages of a bundle use:

```
conduct logs BUNDLE_NAME
```

## Bundle Plugins

The bundle plugin produces ConductR bundles and bundle configurations. sbt-conductr contains several bundle plugin. One of the bundle plugin gets used for your project. Check out the [Plugin Overview](#plugin-overview) section for more information.

### Producing a bundle

To produce a bundle use:

```console
bundle:dist
```

### Scheduling parameters

An application need to provide ConductR scheduling paramters to produce a bundle successfully. These parameters effectively describe what resources are used by your application or service and are used to determine which machine they will run on.

[Play](https://github.com/typesafehub/sbt-conductr/blob/master/src/main/scala/com/lightbend/conductr/sbt/PlayBundlePlugin.scala) and [Lagom](https://github.com/typesafehub/sbt-conductr/blob/master/src/main/scala/com/lightbend/conductr/sbt/LagomBundlePlugin.scala) bundle plugins provide default scheduling paramters. For any other application it is mandatory to specify them. Otherwise the `bundle:dist` command will fail.

#### Defaults

**Play**

* Heap Memory: 128 MiB
* Resident Memory: 256 MiB
* Cpus: 1
* Disk space: 200 MB

**Lagom**

* Heap Memory: 128 MiB
* Resident Memory: 256 MiB
* Cpus: 1
* Disk space: 200 MB 

#### Set custom scheduling paramters

We recommend to specify custom scheduling parameters for each application in your `build.sbt`:

```scala
javaOptions in Universal := Seq(
  "-J-Xmx64m",
  "-J-Xms64m"
)

BundleKeys.nrOfCpus := 2.0
BundleKeys.memory := 128.MiB
BundleKeys.diskSpace := 50.MB
```

The `javaOptions` values declare the maximum and minimum heap size for your application respectively. Profiling your application under load will help you determine an optimal heap size. We recommend declaring the `BundleKeys.memory` value to be approximately twice that of the heap size. `BundleKeys.memory` represents the *resident* memory size of your application, which includes the heap, thread stacks, code caches, the code itself and so forth. On Unix, use the `top` command and observe the resident memory column (`RES`) with your application under load.

`BundleKeys.memory` is used for locating machines with enough resources to run your application, and so it is particularly important to size it before you go to production.

### Bundle configuration

It is possible to produce additional configuration bundles that contain an optional `bundle.conf` the value of which override the main bundle, as
well as arbitrary shell scripts. These additional configuration files must be placed in your project's src/bundle-configuration/default folder.

The bundle-configuration folder may contain many configurations in order to support development style scenarios, the desired configuration can be specified with the setting ("default" is the default folder name) in the `build.sbt`:

```
BundleKeys.configurationName := "default"
```

Then, to produce this additional bundle execute:

```
configuration:dist
```

> Note that bundle configuration that is generally performed from within sbt is therefore part of the project to support developer use-cases. Operational use-cases where sensitive data is held in configuration is intended to be performed outside of sbt, and in conjunction with the [ConductR CLI](https://github.com/typesafehub/conductr-cli#command-line-interface-cli-for-typesafe-conductr) (specifically the `shazar` command).

### Advanced bundles and configuration

sbt-conductr is capable of producing many bundles and bundle configurations for a given sbt module.

#### Adding Start command options

Use the `BundleKeys.startCommand` to add additional options to the start command. Let's say you are using a Play application and want to specify a custom application secret then the option to the `BundleKeys.startCommand`:

```scala
BundleKeys.startCommand += "-Dplay.crypto.secret=dontsharethiskey"
```

Note that memory heap is controlled by the memory BundleKey and heap flags should not be passed here.

#### Renaming an executable

Sometimes you need to invoke something other than the script that the native packager assumes. For example, if you have a script in the bin folder named `start.sh`, and it isn't expecting any Java options:

```scala
BundleKeys.executableScriptPath in Bundle := (file((normalizedName in Bundle).value) / "bin" / "start.sh").getPath
javaOptions in Bundle := Seq.empty
```

#### Extending bundles

Suppose that you have an sbt module where there are multiple ways in which it can be produced. 
[ReactiveMaps](https://github.com/typesafehub/ReactiveMaps) is one such example where the one application can be 
deployed in three ways:

* frontend
* backend-region
* backend-summary

Its frontend configuration is expressed in the regular way i.e. within the global scope:

```scala

// Main bundle configuration

normalizedName := "reactive-maps-frontend"
BundleKeys.nrOfCpus := 2.0
...
```

Thus a regular `bundle:dist` will produce the frontend bundle. 

We can then extend the bundle configuration and overlay some new values for a different target. Here's a sample
of what the backend-region target looks like:

```scala

lazy val BackendRegion = config("backend-region").extend(Bundle)
BundlePlugin.bundleSettings(BackendRegion)
inConfig(BackendRegion)(Seq(
  normalizedName := "reactive-maps-backend-region",
  BundleKeys.configurationName := (normalizedName in BackendRegion).value,
  ...
))
```

A new configuration is created that extends the regular `Bundle` one for the purposes of delegating sbt settings.
Therefore anything declared within the `inConfig` function will have precedence over that which is declared in the
`Bundle` sbt configuration. The `bundleSettings` function defines a few important settings that you need.

To produce the above bundle then becomes a matter of just `backend-region:dist`.

#### Extending bundle configurations

The optional `bundle.conf` file can either be provided directly, or be generated via sbt settings. The following shows
how to create an sbt configuration and then define `bundle.conf` settings. The settings are for a fictitious `backend`
configuration that overrides the bundle name and the roles:

```scala
lazy val Backend = config("backend").extend(BundleConfiguration)
BundlePlugin.configurationSettings(Backend)
inConfig(Backend)(Seq(
  normalizedName := "reactive-maps-backend",
  roles := Set("big-backend-server")
))
```

Note the distinction between the `configurationSettings` and `bundleSettings` for bundle configurations and bundles 
respectively.

You must also associate the configuration with your project:

```
lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .configs(Backend)
```

A configuration for the above can then be generated:

```
backend:dist
```

### Bundle settings

The following bundle settings are provided under the `BundleKeys` object:

Name                  | Description
----------------------|-------------
bundleConf            | The bundle configuration file contents
bundleConfVersion     | The version of the bundle.conf file. By default this is 1.
bundleType            | The type of configuration that this bundling relates to. By default Universal is used.
checkInitialDelay     | Initial delay before the check uris are triggered. The `FiniteDuration` value gets rounded up to full seconds. Default is 3 seconds.
checks                | Declares uris to check to signal to ConductR that the bundle components have started for situations where component doesn't do that. For example `Seq(uri("$WEB_HOST"))` will check that a endpoint named "web" will be checked given its host environment var. Once that URL becomes available then ConductR will be signalled that the bundle is ready. Note that a `docker+` prefix should be used when waiting on Docker components so that the Docker build event is waited on e.g. `Seq(uri("docker+$WEB_HOST"))`<br/>Optional params are: 'retry-count': Number of retries, 'retry-delay': Delay in seconds between retries, 'docker-timeout': Timeout in seconds for docker container start. For example: `Seq(uri("$WEB_HOST?retry-count=5&retry-delay=2"))`.
compatibilityVersion  | A versioning scheme that will be included in a bundle's name that describes the level of compatibility with bundles that go before it. By default we take the major version component of a version as defined by [http://semver.org/]. However you can make this mean anything that you need it to mean in relation to bundles produced prior to it. We take the notion of a compatibility version from [http://ometer.com/parallel.html]."
conductrTargetVersion | The version of ConductR to that this bundle can be deployed on. During bundle creation a compatibility check is made whether this bundle can be deployed on the specified ConductR version. Defaults to 1.1.
configurationName     | The name of the directory of the additional configuration to use. Defaults to 'default'
diskSpace             | The amount of disk space required to host an expanded bundle and configuration. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required.
enableAcls            | Acls can be declared on an endpoint if this setting is 'true'. Otherwise only service endpoints can be declared. Endpoint acls can be used from ConductR 1.2 onwards. Therefore, the default in ConductR 1.1- is 'false' and in ConductR 1.2+ 'true'.
endpoints             | Declares endpoints. The default is Map("web" -> Endpoint("http", 0, Set.empty)). The endpoint key is used to form a set of environment variables for your components, e.g. for the endpoint key "web" ConductR creates the environment variable `WEB_BIND_PORT`.
executableScriptPath  | The relative path of the executableScript within the bundle.
memory                | The amount of resident memory required to run the bundle. Use the Unix `top` command to determine this value by observing the `RES` and rounding up to the nearest 10MiB.
nrOfCpus              | The number of cpus required to run the bundle (can be fractions thereby expressing a portion of CPU). Required.
overrideEndpoints     | Overrides the endpoints settings key with new endpoints. This task should be used if the endpoints need to be specified programmatically. The default is None.
roles                 | The types of node in the cluster that this bundle can be deployed to. Defaults to "web".
startCommand          | Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder. <br/> Example JVM component: </br> `BundleKeys.startCommand += "-Dakka.cluster.roles.1=frontend"` </br> Example Docker component (should additional args be required): </br> `BundleKeys.startCommand += "dockerArgs -v /var/lib/postgresql/data:/var/lib/postgresql/data"` (this adds arguments to `docker run`). Note that memory heap is controlled by the BundleKeys.memory key and heap flags should not be passed here.
system                | A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0. Defaults to the package name.
systemVersion         | A version to associate with a system. This setting defaults to the value of compatibilityVersion.


&copy; Lightbend Inc., 2014-2016
