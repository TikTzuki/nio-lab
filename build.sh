#!/bin/bash

build_aws(){
    a="./nio-server/src/main/resources/cassandra_truststore.jks"
    b="BOOT-INF/classes/cassandra_truststore.jks"
    sed -i -e "s%${a}%${b}%g" nio-server/src/main/resources/keyspaces-application.conf
    cat nio-server/src/main/resources/keyspaces-application.conf
}

build_k8s(){

}

if [ "$1" == "build-aws" ] ; then
    echo "Prepare source AWS"
    build_aws
ifif [ "$1" == "build-k8s" ] ; then
    echo "Prepare source K8s"
    build_k8s
fi