#!/bin/bash

# This script publishes the FastComments Android SDK to Repsy.io
# It requires Repsy credentials

# Exit on error
set -e

# Extract version from command line or use a default
VERSION=${1:-0.0.1}

echo "Publishing version $VERSION to Repsy.io"

# Publish Android SDK
echo "Publishing Android SDK..."
cd ./libraries/sdk && ../../gradlew publish -PreleaseVersion=$VERSION && cd -

echo "Android SDK published successfully to Repsy.io"