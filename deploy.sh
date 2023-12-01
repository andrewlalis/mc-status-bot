#!/usr/bin/env bash

mvn clean package
scp target/mc-status-bot-1.0-SNAPSHOT-shaded.jar root@andrewlalis.com:/opt/mc-status-bot/mc-status-bot.jar
