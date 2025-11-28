#!/bin/bash

set -e

# 로그 파일 설정
LOG_FILE="/home/ubuntu/deploy.log"
exec > >(tee -a $LOG_FILE) 2>&1

# 1. 환경 설정
PROJECT_NAME="vani/springboot"
CONTAINER_NAME="community-springboot-app"
IMAGE_TAG="latest"

AWS_REGION="ap-northeast-2"
AWS_ACCOUNT_ID="658173955655"

PORT_MAPPING="8080:8080"

ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_URI="${ECR_URI}/${PROJECT_NAME}:${IMAGE_TAG}"

echo "=========================================="
echo "Deployment started at $(date)"
echo "=========================================="

# 2. ECR 로그인
echo "Logging in to Amazon ecr..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_URI

# 3. 이미지 가져오기
echo "Pulling Docker image: $IMAGE_URI"
docker pull $IMAGE_URI

# 4. 기존 컨테이너 중지 및 삭제
if [ $(docker ps -a -q -f name=$CONTAINER_NAME) ]; then
  echo "Stopping existing container..."
  docker stop $CONTAINER_NAME
  docker rm $CONTAINER_NAME
fi

echo "AWS Parameter Store에서 환경변수 가져오는 중..."

DB_HOST=$(aws ssm get-parameter --name "/community/db-host" --region ap-northeast-2 --query "Parameter.Value" --output text)
DB_USERNAME=$(aws ssm get-parameter --name "/community/db-username" --region ap-northeast-2 --query "Parameter.Value" --output text)
DB_PASSWORD=$(aws ssm get-parameter --name "/community/db-password" --region ap-northeast-2 --with-decryption --query "Parameter.Value" --output text)
REDIS_HOST=$(aws ssm get-parameter --name "/community/redis-host" --region ap-northeast-2 --query "Parameter.Value" --output text)
JWT_SECRET=$(aws ssm get-parameter --name "/community/jwt-secret" --region ap-northeast-2 --with-decryption --query "Parameter.Value" --output text)

echo "DB_HOST: $DB_HOST"
echo "REDIS_HOST: $REDIS_HOST"


echo "Starting new container..."
docker run -d \
  --name $CONTAINER_NAME \
  --restart always \
  -p $PORT_MAPPING \
  -e DB_HOST=$DB_HOST \
  -e DB_USERNAME=$DB_USERNAME \
  -e DB_PASSWORD=$DB_PASSWORD \
  -e REDIS_HOST=$REDIS_HOST \
  -e JWT_SECRET=$JWT_SECRET \
  -e TZ=Asia/Seoul \
  $IMAGE_URI

echo "Cleaning ip old Docker images..."
docker image prune -af --filter "until=48h"

echo "Current disk usage:"
docker system df

echo "=========================================="
echo "Deployment completed at $(date)"
echo "=========================================="