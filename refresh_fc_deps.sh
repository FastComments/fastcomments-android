#!/bin/bash

./gradlew build --refresh-dependencies -Pdependency=com.fastcomments:core:0.0.1
./gradlew build --refresh-dependencies -Pdependency=com.fastcomments:client:0.0.1
./gradlew build --refresh-dependencies -Pdependency=com.fastcomments:pubsub:0.0.1
