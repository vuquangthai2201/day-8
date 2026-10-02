#!/bin/bash
set -e
export PGDATA=/var/lib/postgresql/data
until pg_isready -h pg-primary -U flashsale; do sleep 1; done
if [ -z "$(ls -A "$PGDATA" 2>/dev/null)" ]; then
  chown -R postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"
  gosu postgres pg_basebackup -D "$PGDATA" -R -X stream -P \
    -d "host=pg-primary user=replicator password=replicator application_name=replica1"
fi
exec gosu postgres postgres -c hot_standby=on
