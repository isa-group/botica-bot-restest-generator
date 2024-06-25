#!/bin/sh
./mvnw clean install && docker build -t botica-bot-restest-generator .
