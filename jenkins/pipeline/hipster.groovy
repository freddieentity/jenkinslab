pipeline {
  // options {
  //   timestamps()
  //   timeout(time: 180, unit: 'MINUTES')
  //   ansiColor('xterm')
  //   disableConcurrentBuilds()
  //   buildDiscarder(logRotator(numToKeepStr: '250', daysToKeepStr: '5'))
  // }

  agent any

  environment {
    DOCKERHUB_USERNAME    = credentials('DOCKERHUB_USERNAME')
    DOCKERHUB_PASSWORD    = credentials('DOCKERHUB_PASSWORD')
    MANIFEST_REPO = 'https://gitlab.com/gitops-freddieentity/manifests' 
    SERVICE_NAME = 'adservice'
  }

  stages {
    stage('Prepare') {
      steps {
        script {
          GIT_COMMIT = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
        }
      }
    }
    stage('Build') {

      steps {
        sh '''#!/usr/bin/env bash
          echo "Shell Process ID: $$"
          docker build --tag ${DOCKERHUB_USERNAME}/${SERVICE_NAME}:${GIT_COMMIT} ./${SERVICE_NAME}
        '''
      }
    }

    stage('Push') {

      steps {
        sh '''#!/usr/bin/env bash
          echo "Shell Process ID: $$"
          echo $DOCKERHUB_PASSWORD | docker login -u $DOCKERHUB_USERNAME --password-stdin
          docker push ${DOCKERHUB_USERNAME}/${SERVICE_NAME}:${GIT_COMMIT}
        '''
      }
    }
    stage('Deploy DEV') {
      steps {
        sh '''#!/usr/bin/env bash
          echo "Shell Process ID: $$"
          git config --global user.email "ci@ci.com"
          git config --global user.name "jenkins-ci"
          rm -rf manifests
          git clone ${MANIFEST_REPO}
          sed -i "s,tag:.*,tag:\ ${DOCKERHUB_USERNAME}/${SERVICE_NAME}:${GIT_COMMIT}," dev/values/${SERVICE_NAME}.yaml
          git commit -am 'Publish new version' && git push || echo 'no changes'
        '''
      }
    }
    stage('Deploy PROD') {
      steps {
        input message:'Approve deployment?'
        sh '''#!/usr/bin/env bash
          sed -i "s,tag:.*,tag:\ ${DOCKERHUB_USERNAME}/${SERVICE_NAME}:${GIT_COMMIT}," dev/values/${SERVICE_NAME}.yaml
          git commit -am 'Publish new version' && git push origin main || echo 'no changes'
        '''
      }
    }
  }
  post {
    failure {
        sh '''#!/usr/bin/env bash
          echo "Shell Process ID: $$"
        '''
        }
    }
}