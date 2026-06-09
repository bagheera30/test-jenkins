pipeline {
    agent any

    environment {
        IMAGE_NAME = "bagheeraid/test-jenkins"
        IMAGE_TAG = "${BUILD_NUMBER}"
        NAMESPACE = "default"
        DEPLOYMENT = "springboot"
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'master',
                url: 'https://github.com/bagheera30/test-jenkins.git'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build \
                    -t ${IMAGE_NAME}:${IMAGE_TAG} .
                """
            }
        }

        stage('Push Docker Image') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh '''
                        echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                    '''
                }
            }
        }

        stage('Deploy Kubernetes') {
            steps {
                sh """
                if kubectl get deployment ${DEPLOYMENT} -n ${NAMESPACE} >/dev/null 2>&1; then
                    echo "Deployment sudah ada"

                    # Restart pod
                    kubectl rollout restart deployment/${DEPLOYMENT} -n ${NAMESPACE}

                    # Tunggu rollout selesai
                    kubectl rollout status deployment/${DEPLOYMENT} -n ${NAMESPACE}
                else
                    echo "Deployment belum ada"

                    # Membuat deployment baru
                    kubectl apply -f k8s/deployment.yaml -n ${NAMESPACE}
                    kubectl apply -f k8s/service.yaml -n ${NAMESPACE}
                fi
                """
            }
        }
    }

    post {
        success {
            echo 'Deployment Success'
        }

        failure {
            echo 'Pipeline Failed'
        }
    }
}