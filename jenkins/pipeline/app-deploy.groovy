pipeline {
  options {
    timestamps()
    timeout(time: 180, unit: 'MINUTES')
    ansiColor('xterm')
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '250', daysToKeepStr: '5'))
  }

    agent any

    environment {
        CONTAINER_REPOSITORY= "freddieentity"
        APPLICATION_SOURCE_URL = 'https://github.com/freddieentity/nginx-service.git'
        APPLICATION_NAME = "nginx-service"
        CONTAINER_BUILD_CONTEXT = "."
        APPLICATION_DOMAIN="https://example.com"

        SONAR_SCANNER = tool 'sonarqube-scanner'
    }
    stages {
        stage ('Git Checkout') {

            steps {
                echo 'Git Checkout'            
                git branch: 'main',
                    url: "${APPLICATION_SOURCE_URL}"
                    // credentialsId: 'githubToken'
                script {
                GIT_COMMIT = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
                }
            }
        }

        stage('UnitTest') {
            steps {
                echo 'UnitTesting..'
            }
        }
        stage('SCA') {
            steps {
                echo 'SCA Scanning..'
            }
        }
        stage('SAST') {
            steps {
                echo 'SAST Scanning..'
                withSonarQubeEnv('SonarQube') {
                    sh """
                    ${SONAR_SCANNER}/bin/sonar-scanner \
                        -Dsonar.projectKey=${APPLICATION_NAME} \
                        -Dsonar.sources=. \
                        -Dsonar.host.url=http://sonarqube:9000 \
                        -Dsonar.login=sqp_fdffa5bd6a3c2b909bbc686d1bd3f9b7decbc57a
                    """
                }
                timeout(time: 2, unit: 'MINUTES') {
                    script {
                        waitForQualityGate abortPipeline: true
                    }
                }

            }
        }
        stage('Build') {
            steps {
                echo 'Building..'
                withDockerRegistry([credentialsId: "CONTAINER_REGISTRY_CREDENTIALS", url: ""]) {
                    sh "docker build -t ${CONTAINER_REPOSITORY}/${APPLICATION_NAME}:${GIT_COMMIT} ${CONTAINER_BUILD_CONTEXT}"
                    sh "docker push ${CONTAINER_REPOSITORY}/${APPLICATION_NAME}:${GIT_COMMIT}"
                }
            }
        }
        stage('ImageScan') { 
            steps {
                echo 'Container Image Scaning....'
                sh "docker run --rm aquasec/trivy image ${CONTAINER_REPOSITORY}/${APPLICATION_NAME}:${GIT_COMMIT}"
            }
        }
        stage('Deploy') { 
            steps {
                echo 'Deploying....'
                // parallel(
                //     "Deployment": {
                //         withKubeConfig([credentialsId: 'KUBECONFIG']) {
                //             // sh "sed -i "s#replace#${GIT_COMMIT}#g" deployment.yaml"
                //             // sh "kubectl -n default apply -f deployment.yaml"
                //             sh "kubectl version --short"
                //             sh "kubectl get nodes -o wide"
                //         }
                //     }
                //     // "Rollout Status": {
                //     //     withKubeConfig([credentialsId: 'KUBECONFIG']) {
                //     //         scripts {
                //     //             // sleep 60s

                //     //             // if [[ $(kubectl -n default rollout status deploy ${deploymentName} --timeout 5s) != *"successfully rolled out"* ]]; 
                //     //             // then
                //     //             //     echo "Deployment ${deploymentName} Rollout has Failed"
                //     //             //     kubectl -n default rollout undo deploy ${deploymentName}
                //     //             //     exit 1;
                //     //             // else
                //     //             //     echo "Deployment ${deploymentName} Rollout is Success"
                //     //             // fi
                //     //         }
                //     //     }
                //     // }
                // )
            }
        }
        stage('DAST') {
            steps {
                echo 'DAST Scanning..'
                sh "docker run --rm -t softwaresecurityproject/zap-weekly zap-baseline.py -t ${APPLICATION_DOMAIN}"
            }
        }
    }
    post { 
        always { 
            cleanWs()
        }
    }
}