#!/usr/bin/env bash

# Install OpenJDK 25 via apt
echo "Installing OpenJDK 25..."
sudo apt-get update
sudo apt-get install -y openjdk-25-jdk

# Configure JAVA_HOME for Maven
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Verify Java and Maven are using the correct version
java -version
mvn -v

# Validate the setup by attempting a build
mvn package
