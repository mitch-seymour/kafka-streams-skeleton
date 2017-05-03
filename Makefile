.PHONY: help
.DEFAULT_GOAL := help

DOCKER_IMAGE_NAME=$(shell ./docker/script/build docker/ getImageName 2>/dev/null)
DOCKER_MACHINE_IP=$(shell docker-machine ip kafka-cluster)
DOCKER_MACHINE_STATUS=$(shell docker-machine status kafka-cluster 2> /dev/null)
DOCKER_COMPOSE_PROCESSES=$(shell docker-compose ps | grep -v Exit | grep Up | wc -l | awk {'print $1'})

help:
	@grep -h -P '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

###################################################
#                                                 #
#                    Install                      #
#                                                 #
###################################################

###################################################
#                                                 #
#                    Checks                       #
#                                                 #
###################################################
checks: check_docker_machine check_docker_compose

check_docker_machine:
	@case $$(docker-machine status kafka-cluster 2> /dev/null) in \
        "")      echo "Creating Docker machine" && docker-machine create --driver virtualbox kafka-cluster;; \
        Stopped) echo "Starting machine" && docker-machine start kafka-cluster ;; \
        Running) echo "Docker machine is running at $(shell docker-machine ip kafka-cluster)" ;; \
        *)         echo "Unknown Docker machine state" ;; \
    esac

check_docker_machine_env:
	@eval $$(docker-machine env kafka-cluster)

check_docker_compose: check_docker_machine check_docker_machine_env
	@if [ $(DOCKER_COMPOSE_PROCESSES) -eq 0 ]; then\
		echo "Starting docker-compose containers" && docker-compose up -d;\
	else\
	    echo "docker-compose containers are running";\
	fi

docker_image_name: ## Get the docker image name
	@echo $(DOCKER_IMAGE_NAME)

###################################################
#                                                 #
#                    Cleaning                     #
#                                                 #
###################################################
clean: clean_build clean_docker

clean_build: ## Removes the build directory
	@./gradlew clean

clean_docker: ## Removes the local copy of the docker image
	@rm -rf docker/files/rpm/*
	@if [ -z $(shell docker images --format "{{.Repository}}:{{.Tag}}" | grep "$(DOCKER_IMAGE_NAME)") ]; then\
		echo "No image to remove: $(DOCKER_IMAGE_NAME)";\
	else\
		docker rmi -f $(DOCKER_IMAGE_NAME);\
	fi

###################################################
#                                                 #
#                   Packaging                     #
#                                                 #
###################################################
package: ## Builds the RPM
	@./gradlew --rerun-tasks createRpm
	@cp build/distributions/*.rpm docker/files/rpm

docker_image: ## Builds the docker image locally
	@./docker/script/build docker/
	@./docker/script/build docker/ getImageName 2>/dev/null

###################################################
#                                                 #
#                    Running                      #
#                                                 #
###################################################
run: check_docker_compose
	./gradlew runLocal \
        -Dkafka.streams.bootstrap.servers=$(DOCKER_MACHINE_IP):9092 \
        -Dkafka.streams.zookeeper.connect=$(DOCKER_MACHINE_IP):2181

run_docker: check_docker_compose
	docker run  \
        -e "KAFKA_STREAMS_APPLICATION_ID=myproject_dev_dev" \
        -e "KAFKA_STREAMS_BOOTSTRAP_SERVERS=$(DOCKER_MACHINE_IP):9092" \
        -e "KAFKA_STREAMS_ZK_CONNECT=$(DOCKER_MACHINE_IP):2181" \
        -e "GRAPHITE_REPORTING_ENABLED=false" \
        -ti $(DOCKER_IMAGE_NAME) \
        myproject \
        -Dconfig.file=/etc/myproject/application.conf \
        -Dlog4j.configurationFile=/etc/myproject/log4j2.properties

attach_docker:
	@docker run  \
        -e "KAFKA_STREAMS_APPLICATION_ID=myproject_dev_dev" \
        -e "KAFKA_STREAMS_BOOTSTRAP_SERVERS=172.16.21.150:9092" \
        -e "KAFKA_STREAMS_ZK_CONNECT=172.16.21.150:2181/kafka" \
        -ti $(DOCKER_IMAGE_NAME) \
        /bin/bash

###################################################
#                                                 #
#                Tests / Examples                 #
#                                                 #
###################################################

test_producer: check_docker_compose
	./gradlew runTestProducer \
        -Dkafka.streams.bootstrap.servers=$(DOCKER_MACHINE_IP):9092

unit_tests: ## Runs the gradle tests
	@./gradlew --rerun-tasks test
