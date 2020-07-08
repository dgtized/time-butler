#!/bin/bash

rm -rf classes
mkdir -p classes
clojure -e "(compile 'time-butler.cli)"
clojure -A:uberdeps  --main-class time_butler.cli --deps-file deps.edn --target time-butler.jar
