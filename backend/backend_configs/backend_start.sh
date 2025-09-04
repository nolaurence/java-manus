#!/bin/bash
set -e

#sleep 2
APP_NAME=case-management-backend
JAR_VERSION=0.0.1-SNAPSHOT

# 设置环境
ENVIRONMENT=local

if [ "$ENVIRONMENT" == "prod" ]; then
    APP_PORT=7001          # 生产环境的应用端口
    JAVA_OPTS=""           # 生产环境不带 -Dspring.profile.active
    APP_HOME=/app # 从package.tgz中解压出来的jar包放到这个目录下
else
    APP_PORT=7002          # 非生产环境的应用端口
    JAVA_OPTS="-Dspring.profiles.active=${ENVIRONMENT} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"  # 非生产环境带 -Dspring.profile.active
    APP_HOME=/app # 从package.tgz中解压出来的jar包放到这个目录下
fi
HEALTH_CHECK_URL=http://127.0.0.1:${APP_PORT}  # 应用健康检查URL
JAR_NAME=${APP_HOME}/target/${APP_NAME}-${JAR_VERSION}.jar # jar包的名字
JAVA_OUT=/app/logs/backend_java.log  #应用的启动日志

CHROME_OUT=/app/logs/backend_chrome.log  # Chrome 的启动日志

# 启动 Java 应用
mkdir -p /app/logs

# shellcheck disable=SC2024
sudo env PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 nohup java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar ${JAR_NAME} --spring.profiles.active=backend > ${JAVA_OUT} 2>&1 &
#sudo env PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 nohup java $JAVA_OPTS -jar ${JAR_NAME} --spring.profiles.active=backend > ${JAVA_OUT} 2>&1 &
echo "Java app started"

# 等待所有后台进程
wait