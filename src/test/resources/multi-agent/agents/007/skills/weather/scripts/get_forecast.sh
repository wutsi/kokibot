#!/bin/bash

curl "https://api.open-meteo.com/v1/forecast?latitude=$1&longitude=$2&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m"
