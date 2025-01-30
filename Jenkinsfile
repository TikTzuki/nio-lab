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
    "return['client','server']"
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
    "return['AWS','K8S']"
    ]]
    ],
    ]
)
])

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
        HASH_TAG = "${BRANCH}.${COMMIT_HASH}.${BUILD_NUMBER}".drop(7)
        TAG_VERSION = "${HASH_TAG}"
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
    }
}