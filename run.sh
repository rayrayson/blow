#!/bin/bash
set -e
set -u
set -o errexit

#
# Compile
#
groovy build.groovy compile

#
# Compose the classpath
#
CLASSPATH="./build/stage/classes"
for f in ./build/stage/lib/*.jar; do
  CLASSPATH="${CLASSPATH}:$f";
done
for f in ./build/stage/embed/*.jar; do
  CLASSPATH="${CLASSPATH}:$f";
done

#
# Invoke the main class 
#
java -cp "$CLASSPATH" blow.shell.BlowShell "$@"

