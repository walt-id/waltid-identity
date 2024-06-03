#!/usr/bin/env sh

docker run -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_DB=postgres -e POSTGRES_PASSWORD=postgres postgres
