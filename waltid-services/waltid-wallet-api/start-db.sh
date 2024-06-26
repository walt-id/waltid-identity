#!/usr/bin/env sh

docker run -p 5432:5432 -e POSTGRES_USER=waltid -e POSTGRES_DB=waltid -e POSTGRES_PASSWORD=waltid postgres
