# time-butler

A cross-build monitoring tool for identifying performance bottlenecks and
non-deterministic tests.

Uploads step timing and aggregate test results from builds and deploys to S3,
and then generates graphs of times and success/failure/flake rates over time. It
identifies non-deterministic tests by comparing test results with the commit
SHA, and logs any tests that have different results attached to the same SHA
from later builds. After identifying flaking tests it can be configured to
report them to Rollbar, allowing teams to assign ownership to fix them.

## Installation

install [clojure](https://clojure.org/guides/getting_started) & the Java Development Kit (JDK):

    $ git clone git@github.com:dgtized/time-butler.git
    $ cd time-butler

## Usage

    $ clj -m time-butler.cli --config resources/config.edn

The default report is generated in `output/index.html`.

It can also compiled into a uberjar and executed as follows:

    $ bin/package.sh
    $ java -jar time-butler.jar -h

## Environment Variables

 * `BUTLER_BUCKET`: S3 bucket to fetch builds from
 * `BUTLER_CACHE`: local directory for storing synchronizing builds
 * `BUTLER_ROLLBAR`: Rollbar token to submit spec failures as events. See `https://rollbar.com/$project/settings/access_tokens/` for tokens.

## Rollbar Integration

Preliminary support for reporting spec failure occurences to Rollbar. Rollbar is used to track spec flakes long term and look at relative occurrence of individual flakes.

This is enabled locally provided that `BUTLER_ROLLBAR` is specified and an environment is specified ala `--rollbar development`.

# Structure of Data Inputs

The S3 bucket or local cache should contain a list of directories of format:

    $branch-$build-$timestamp

The `:branches` & `:period` configuration in `config.edn` will download and process matching branches for the specified time period.

As example, a bare bones `config.edn` might look something like:

```
{:project {:github "project/name"}
 :storage {:bucket "ci-builds.s3.com"
           :local-cache "jenkins-builds"}
 :synthetic-fields {"compile" :parallel "test" :parallel "deploy" :sequential}
 :builds
 {:branches ["master"]
  :period :fortnight
  :charts [{:title "All Stages" :file-name "all-stages"
            :series [:duration "compile" "test"]}
           {:title "Compilation" :file-name "compilation"
            :series ["compile.assets" "compile.rubocop"]}
           {:title "Testing" :file-name "testing"
            :series ["test.karma" "test.rspec"]}]}
 :deploys
 {:branches ["deploy_to_staging" "deploy_to_production"]
  :period :week
  :charts [{:title "All Stages" :file-name "deploy.all-stages"
            :series [:duration "compile" "test" "deploy"]}
           {:title "Deploy" :file-name "testing"
            :series ["deploy.build-artifact" "deploy.copy-artifact" "deploy.restart"]}]}}
```

would download all directories prefixed with `master` with a timestamp within the last fortnight and process those as builds. It would also download every instrumented build prefixed with `deploy_to_staging` or `deploy_to_production` over the last week and process it as a deploy.

`:project` keys specify site level configuration;

 * `:github` specifies user/project or organization/project for generating
   source code links to github, referenced in failing specs.

`:storage` keys provide default values for `BUTLER_CACHE` and `BUTLER_BUCKET`. Note that s3 bucket configuration is mandatory, either through environment variables or config file.

### Manifest

Each build directory contains a `manifest.json` which is of the following format:

```json
{
  "BUILD_URL": "https://jenkins.url/job/project/job/manifest/2/",
  "TIMESTAMP": {
    "STARTED": 1533226167,
    "COMPLETED": 1533229809
  },
  "REVISION": {
    "HEAD": "ec33444d",
    "MASTER": "746f6950"
  },
  "MANIFEST": {
    "VERSION": "1.0.0",
    "ACTION": "build",
    "ASSETS": "assets.json",
    "STEP_TIMINGS": "step-timings.json"
  },
  "ENVIRONMENT": {
    "CACHE_TARBALL": "v2.tgz",
    "NODE_NAME": "n1",
    "PARALLELISM": 6,
    "PERCY_ENABLE": 0,
    "SYSCONFCPUS_PROCESSORS": 2
  }
}
```

The sections are used as follows:

 * `BUILD_URL` is used for linking back to the source build in Jenkins.
 * `TIMESTAMP` is self-explanatory, containing start and completion timestamps
 * `REVISION` contains the current `HEAD` & `MASTER` revisions at the time of the CI. For deploy tracking, `BRANCH_SOURCE` refers to the revision being reset or merged to. `HEAD` should always exist, and for CI, `MASTER` is important as it is necessary to uniquely identify the build. A branch may be re-run without moving but master may have changed position, and so we need to ensure both positions are fixed to detect code changes. When monitoring branch master, they are expected to be equal.
 * `MANIFEST` contains meta information for parsing the build.
 ** The `VERSION` field is to allow upgrades between client & time-butler,
 ** `ACTION` is either `build` or `deploy` to help distinguish between build types.
 ** `ASSETS` references the `assets.json` location
 ** `STEP_TIMINGS` references the `step-timings.json` location
 * `ENVIRONMENT` contains various metadata about the build process like which node the build occurred on that may be useful for correlation at a later date.

### Step Timings

JSON file for timing each step using the following format:

```json
[{"name":"bootstrap.a","real":15.23,"user":14.9,"system":1.91,"elapsed":"0:15.23","cpu":110},
{"name":"bootstrap.b","real":11.18,"user":1.8,"system":0.24,"elapsed":"0:11.18","cpu":18}]
```

Steps are referenced hierarchically separated by ".", and for the moment only `real` is used to compare time spent in each step.

## Development


Use [leiningen](https://leiningen.org/) to generate a namespace diagram:

    $ bin/lein hiera


### Testing

Run tests by executing:

```
bin/kaocha
```

## Changelog

### 0.15.3

 * Upgrade AWS dependencies
 * Include bin/install-clj to ease install from Jenkins
 * Switch to kaocha for testing
 * Tooling improvements to support running TimeButler from within another repo

### 0.15.2

 * Move styles into linked time-butler.css, add row striping for tables, and other tweaks
 * Sort failing step groups in descending order of frequency
 * Calculate frequency of failing causes groups
 * Count frequency of failing spec contributing to a flaking build

### 0.15.1

 * Log error on malformed JUnit test file, ignore the file and complete report
 * Upgrade dependencies

### 0.15.0

 * Use tools.deps.alpha toolchain in preference to leiningen
 * Remove dependency on environ and load version from project.clj manually

### 0.14.7

 * Only show flake and failing spec lists if there are examples to display
 * Lift github project into configuration edn
 * Fix node-durations.csv link

### 0.14.6

 * Fix flake-report bug from incorrect argument order

### 0.14.5

 * Continued work in converting into a library, lifting up configuration
 * Fix for time periods with only a single deploy to a given stack

### 0.14.3

 * Add mean/median/90th percentile for build summary stats (thanks @brookeangel)
 * Added cljsfmt dependency

### 0.14.2

 * Upgraded clojure and aws dependencies
 * Removed Elm specific "percentage of build" calculation
 * Updated chart configuration
 * Remove duplicate checkout from Jenkinsfile

### 0.14.1

 * Collapse consecutive builds into ellipses when rendering a list

### 0.14.0

 * Replaced clj-time usage with clojure.java-time
 * Added deploy metrics for the last week
 * Link spec names to associated Rollbar item
 * Update Clojure & AWS project dependencies

### 0.13.0

 * Move some leveling functions from time-butler.main into time-butler.timings
 * Extract charts from time-butler.chart into time-butler.chart.{builds,deploys,specs}
 * Move compare-branches into time-butler.compare
 * Move some clj-time dependent helpers into time-butler.gtime

### 0.12.0

 * Load branch & period configuration from config.edn
 * Generate charts for deploys (as specified in config.edn)
 * Refactor manifest parsing as separate from deploy/build parsing
 * Add command-line option `--no-sync` to disable downloading from s3 bucket for faster iteration in development
 * Added a bare-bones clojure.spec for generating builds for local testing

### 0.11.0

 * Introduce config.edn to externalize the declarative chart configuration
 * Bump various libraries, notably clojure 1.10-beta3, aws-sdk, and lein 2.8.1
 * Use tools.logging across the application
 * Add a graph and csv comparing build time across CI nodes
 * Update default chart fields to incorporate all current NRI readings
 * Group causes of build failure for identifying flakes other than specs

### 0.10.1

 * Stop supporting deprecated environment.json builds and do some other cleanups
 * Bump amazonica to 0.3.132 (+ aws sdk deps)

### 0.10.0

 * Exclude "catastrophic builds" from contributing to spec lists on flake page and rollbar.
 * Include failure count in title hover on build links
 * Don't report specs from the last failing builds to rollbar to give them time to converge to flakes.

### 0.9.5

 * Support manifest.json and step-timings.json parsing by default
 * Represent times as seconds except for in incanter datasets where it is converted to minutes
 * Improve step timing summary from raw json to a sorted, formatted table
 * Stack build graph by success, flake, failure
 * Refactor chart rendering to compile from a map
 * Change duration of build by result graph to a scatter plot

### 0.9.0

 * Report spec failure occurences to Rollbar with flake classification

[future ideas](./todo.org)

## License

Copyright © 2020-2021 Charles L.G. Comstock

Distributed under the MIT Public License, see LICENSE file
