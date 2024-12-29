#!/bin/sh

set -eu pipefail
set echo off

# functions
ping()
{
    local service=$1
    local port=$2
    shift; shift;
    COMMENTS=$@
    echo "Pending $service-api"
    until $(nc -zv localhost "$port"); do
        printf '.'
        sleep 5
    done
    echo "$service-api livez!"
}

# main
ping wallet 7001
ping issuer 7002
ping verifier 7003