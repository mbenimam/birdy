# MySQL with Birdy's schema baked in, so a host that only has the images (Coolify, CI) needs no bind mount.
# The init scripts run once, when the data volume is empty.
FROM mysql:8.4.11
COPY schema.sql /docker-entrypoint-initdb.d/01-schema.sql
