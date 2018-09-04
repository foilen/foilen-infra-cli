# About

A CLI to help managing the infrastructure.

# Local testing

```
./create-local-release.sh

docker run -ti \
  --rm \
  --volume /home:/home \
  --workdir $(pwd) \
  --user $(id -u) \
  foilen-infra-cli:master-SNAPSHOT
```

# Start it

```
docker run -ti \
  --rm \
  --volume /home:/home \
  --workdir $(pwd) \
  --user $(id -u) \
  foilen/foilen-infra-cli
```
