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
            [
                $class: 'ChoiceParameter',
                choiceType: 'PT_SINGLE_SELECT',
                name: 'GitBranch',
                script: [
                    $class: 'GroovyScript',
                    fallbackScript: [
                        classpath:[],
                        sandbox: false,
                        script: 'return ["develop"]'
                    ],
                    script: [
                        classpath:[],
                        sandbox: false,
                        script: '''
        import jenkins.model.*
        def jenkinsCredentials = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
                com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials.class,
                Jenkins.instance,
                null,
                null
        );
        def userpass = jenkinsCredentials.findResult { it.id == "tiktzuki-github" ? it : null }
        def gettags = ("git ls-remote -t -h https://" + userpass.username + ":" + userpass.password + "@github.com/TikTzuki/nio-lab.git").execute()
        return gettags.text.readLines()
    '''
                    ]
                ]]
        ]
    )
])
//.collect { it.split()[1] }
//.replaceAll("\\\\^\\\\{\\\\}", '')
def buildDocker(context, imageName) {
    script {
        dockerImage = docker.build(imageName, context)
    }
    script {
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
        NIO_CLIENT = 'nio-client'
        NIO_SERVER = 'nio-server'

        CLIENT_DIR_NAME = 'mock-client'
        SERVER_DIR_NAME = 'nio-server'

        GITHUB_CREDENTIALS = 'tiktzuki-github'

        COMMIT_HASH = sh(returnStdout: true, script: 'git rev-parse --short=4 HEAD').trim()
        BUILD_BRANCH = "${params.BuildBranch}"
        BUILD_TARGET = "${params.BuildTarget}"
        DEPLOY_TARGET = "${params.DeployTarget}"
        HASH_TAG = "${DEPLOY_TARGET}.${COMMIT_HASH}.${BUILD_NUMBER}"
        TAG_VERSION = "${DEPLOY_TARGET}"
        GHP_TOKEN = credentials('tiktuzki-gh-token')
        REGISTRY_CREDENTIAL = "dockerhub-tiktuzki"
    }
    stages {
        stage('Checkout') {
            steps {
                script{
                    if(!params.BuildBranch?.trim()){
                        currentBuild.result = 'ABORTED'
                        error("BuildBranch is not defined")
                    }

                    git branch: "${params.BuildBranch}",
                    credentialsId: "${env.GITHUB_CREDENTIALS}",
                    url: "${GIT_URL}"
                }
            }
        }
        stage('Printing selected choice') {
            steps {
                echo sh(script: 'env|sort', returnStdout: true)
                container('gradle'){
                    script {
                        println(BUILD_BRANCH + "-" + BUILD_TARGET + "-" + DEPLOY_TARGET)
                    }
                }
            }
        }
        stage ('Modify source') {
            steps {
                container('jnlp'){
                    script{
                        if(BUILD_TARGET == NIO_SERVER){
                            sh '''
                            curl --create-dirs -o nio-server/root/.aws/credentials https://x-access-token:$GHP_TOKEN@raw.githubusercontent.com/TikTzuki/config-repos/refs/heads/master/nio-lab/server/.aws/credentials
                            curl -o nio-server/root/.aws/config https://x-access-token:$GHP_TOKEN@raw.githubusercontent.com/TikTzuki/config-repos/refs/heads/master/nio-lab/server/.aws/config

                            a="./nio-server/src/main/resources/cassandra_truststore.jks"
                            b="BOOT-INF/classes/cassandra_truststore.jks"
                            sed -i -e "s%${a}%${b}%g" nio-server/src/main/resources/keyspaces-application.conf

                            # validate
                            cat nio-server/root/.aws/credentials
                            cat nio-server/root/.aws/config
                            cat nio-server/src/main/resources/keyspaces-application.conf
                        '''
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
                        case NIO_CLIENT:
                        sh 'gradle clean mock-client:bootJar'
                        break
                        case NIO_SERVER:
                        sh 'gradle clean nio-server:bootJar'
                        break
                        }
                    }
                }
            }
        }
        stage('Build'){
            steps {
                container('docker-in-docker') {
                    script {
                        def context = (BUILD_TARGET == NIO_CLIENT) ? CLIENT_DIR_NAME : SERVER_DIR_NAME;
                        buildDocker(context, "tiktuzki/${BUILD_TARGET}")
                    }
                }
            }
        }
    }
}