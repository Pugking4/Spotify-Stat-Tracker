# Use official Postgres image version 18
FROM postgres:18

# Copy the initialization SQL schema into the Docker entrypoint initialization directory
COPY ./schema.sql /docker-entrypoint-initdb.d/schema.sql

# The default Postgres environment variables could alternatively be defined in docker-compose.yml 
# and not duplicated here

# Expose the default Postgres port
EXPOSE 5432
volumes:
  - track-database:/var/lib/postgresql
