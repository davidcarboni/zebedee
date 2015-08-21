#!/usr/bin/env bash

export JAVA_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=8002,server=y,suspend=n"
export PORT="8082"

# Restolino configuration
export RESTOLINO_CLASSES="zebedee-reader/target/classes"
export PACKAGE_PREFIX=com.github.onsdigital.zebedee.reader.api
export CONTENT_DIR="content"

# Development: reloadable
mvn clean package dependency:copy-dependencies && \
java $JAVA_OPTS \
 -Drestolino.classes=$RESTOLINO_CLASSES \
 -Dcontent_dir=$CONTENT_DIR \
 -DSTART_EMBEDDED_SERVER=Y \
 -Drestolino.packageprefix=$PACKAGE_PREFIX \
 -cp "zebedee-reader/target/dependency/*" \
 com.github.davidcarboni.restolino.Main

