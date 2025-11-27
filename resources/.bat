sudo docker run -d \
  --name track-database \
  -e POSTGRES_USER=pugking4 \
  -e POSTGRES_PASSWORD=apples \
  -e POSTGRES_DB=track-database \
  -p 5433:5432 \
  -v track-database:/var/lib/postgresql/data \
  postgres:18
