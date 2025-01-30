#!/usr/bin/env groovy

properties([
parameters(
    [
    [
    $class: 'ChoiceParameter',
    choiceType: 'PT_SINGLE_SELECT',
    name: 'BuildTarget',
    script: [$class: 'GroovyScript',
    script: [
    classpath: [],
    sandbox: true,
    script:
    "return['nio-server','nio-client']"
    ]]
    ],
    [
    $class: 'ChoiceParameter',
    choiceType: 'PT_SINGLE_SELECT',
    name: 'DeployTarget',
    script: [$class: 'GroovyScript',
    script: [
    classpath: [],
    sandbox: true,
    script:
    "return['k8s','aws']"
    ]]
    ],
    ]
)
])
def buildDocker(context, imageName) {
    script {
        dockerImage = docker.build(imageName, context)
    }
    script {
        sh 'cat nio-server/src/main/resources/keyspaces-application.conf'
        docker.withRegistry('https://registry.hub.docker.com', REGISTRY_CREDENTIAL) {
            //dockerImage.push(HASH_TAG)
            dockerImage.push(TAG_VERSION)
            dockerImage.push("latest")
        }
    }
}
pipeline {
    agent {
        kubernetes {
            yamlFile "k8s/KubernetesPod.yaml"
            retries 0
        }
    }
    parameters {
        string(name: 'BuildBranch', defaultValue: 'develop', description: 'Branch to build')
    }
    environment {
        COMMIT_HASH = sh(returnStdout: true, script: 'git rev-parse --short=4 HEAD').trim()
        BUILD_BRANCH = "${params.BuildBranch}"
        BUILD_TARGET = "${params.BuildTarget}"
        DEPLOY_TARGET = "${params.DeployTarget}"
        HASH_TAG = "${DEPLOY_TARGET}.${COMMIT_HASH}.${BUILD_NUMBER}"
        TAG_VERSION = "${DEPLOY_TARGET}"
        GHP_TOKEN = credentials('tiktzuki-github')
        REGISTRY_CREDENTIAL = "dockerhub-tiktuzki"
    }
    stages {
        stage('Checkout') {
            steps {
                script{
                    git branch: "${params.BuildBranch}",
                    credentialsId: "tiktzuki-github",
                    url: "${GIT_URL}"
                }
            }
        }
        stage('Printing selected choice') {
            steps {
                echo sh(script: 'env|sort', returnStdout: true)
                container('gradle'){
                    script {
                        println(BUILD_BRANCH)
                        println(BUILD_TARGET)
                        println(DEPLOY_TARGET)
                    }
                }
            }
        }
        stage('Modifiy source'){
            steps {
                container('jnlp'){
                    script{
                        sh "curl --create-dirs -o nio-server/.aws/credentials https://x-access-token:$GHP_TOKEN@raw.githubusercontent.com/TikTzuki/config-repos/refs/heads/master/nio-lab/server/credentials"
                        switch(BUILD_TARGET){
                        case 'aws':
                        sh '''
                            a="./nio-server/src/main/resources/cassandra_truststore.jks"
                            b="BOOT-INF/classes/cassandra_truststore.jks"
                            sed -i -e "s%${a}%${b}%g" nio-server/src/main/resources/keyspaces-application.conf
                            cat nio-server/src/main/resources/keyspaces-application.conf
                        '''
                        case 'k8s':
                        sh '''
                           echo build for k8s
                        '''
                        default:
                        echo 'No build target selected'
                        }
                    }
                }
            }
        }
        stage('Assemble'){
            steps {
                container('gradle'){
                    script{
                        switch(BUILD_TARGET){
                        case 'nio-client':
                        sh 'gradle clean mock-client:bootJar'
                        break
                        case 'nio-server':
                        sh 'gradle clean nio-server:bootJar'
                        break
                        }
                    }
                }
            }
        }
        stage('Build'){
            steps {
                container('jnlp'){
                    container('docker-in-docker') {
                        script {
                            def context = (BUILD_TARGET == 'nio-client') ? 'mock-client' : 'nio-server';
                            buildDocker(context, "tiktuzki/${BUILD_TARGET}")
                        }
                    }
                }
            }
        }
    }
}