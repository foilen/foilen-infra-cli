#!/bin/bash

DM_USERNAME=infra_docker_manager
DM_DATA_PATH=/home/$DM_USERNAME/data
DM_INTERNAL_DATABASE_PATH=$DM_DATA_PATH/db
DM_PERSISTED_CONFIG_PATH=$DM_DATA_PATH/persistedConfig
DM_IMAGE_BUILD_PATH=$DM_DATA_PATH/imageBuild

DOCKER_MANAGER_VERSION=foilen/foilen-infra-docker-manager:${version}

docker rm -f infra_docker_manager

docker run \
  --detach \
  --restart always \
  --env HOSTFS=/hostfs/ \
  --env CONFIG_FILE=/data/config.json \
  --volume $DM_DATA_PATH:/data \
  --volume /:/hostfs/ \
  --volume /usr/bin/docker:/usr/bin/docker \
  --volume /usr/lib/x86_64-linux-gnu/libltdl.so.7.3.1:/usr/lib/x86_64-linux-gnu/libltdl.so.7 \
  --volume /var/run/docker.sock:/var/run/docker.sock \
  --hostname $(hostname -f) \
  --workdir /data \
  --log-driver json-file --log-opt max-size=100m \
  --name infra_docker_manager \
  $DOCKER_MANAGER_VERSION --debug
  