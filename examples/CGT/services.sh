#!/bin/bash

source ./nodes_config.cfg        # To configure the nodes [ip addresses] of the containers running
source ./cgt_config.cfg     # To configure settings for the call-graph-tracking process

sudo docker run -e "REGISTRY_HOST=$NODE_3" -e "REGISTRY_PORT=10000" -e "HOST_NAME=$NODE_2" -e "SERVICE_PORT=1111" -e "CGT_HOST=$NODE_1" -e "CGT_PORT=8081" -e "CGT_FREQ=$FERQUENCY" -e "CGT_BATCH=$BATCH_SIZE" -e "DB_HOST=$NODE_0" -e "DB_PORT=3306" -p 1111:8080 -d mohanna/teastore-persistence:development
sudo docker run -e "REGISTRY_HOST=$NODE_3" -e "REGISTRY_PORT=10000" -e "HOST_NAME=$NODE_2" -e "SERVICE_PORT=2222" -e "CGT_HOST=$NODE_1" -e "CGT_PORT=8081" -e "CGT_FREQ=$FERQUENCY" -e "CGT_BATCH=$BATCH_SIZE" -p 2222:8080 -d mohanna/teastore-auth:development
sudo docker run -e "REGISTRY_HOST=$NODE_3" -e "REGISTRY_PORT=10000" -e "HOST_NAME=$NODE_2" -e "SERVICE_PORT=3333" -e "CGT_HOST=$NODE_1" -e "CGT_PORT=8081" -e "CGT_FREQ=$FERQUENCY" -e "CGT_BATCH=$BATCH_SIZE" -p 3333:8080 -d mohanna/teastore-recommender:development
sudo docker run -e "REGISTRY_HOST=$NODE_3" -e "REGISTRY_PORT=10000" -e "HOST_NAME=$NODE_2" -e "SERVICE_PORT=4444" -e "CGT_HOST=$NODE_1" -e "CGT_PORT=8081" -e "CGT_FREQ=$FERQUENCY" -e "CGT_BATCH=$BATCH_SIZE" -p 4444:8080 -d mohanna/teastore-image:development
sudo docker run -e "REGISTRY_HOST=$NODE_3" -e "REGISTRY_PORT=10000" -e "HOST_NAME=$NODE_2" -e "SERVICE_PORT=8080" -e "CGT_HOST=$NODE_1" -e "CGT_PORT=8081" -e "CGT_FREQ=$FERQUENCY" -e "CGT_BATCH=$BATCH_SIZE" -p 8080:8080 -d mohanna/teastore-webui:development

echo "Instrumented Docker containers are running!"