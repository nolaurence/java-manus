#!/bin/bash

# 修改APP_NAME为云效上的应用名
APP_NAME=case-management-backend
JAR_VERSION=0.0.1-SNAPSHOT

PROG_NAME=$0
ACTION=$1
ENVIRONMENT=${2:-pre} # 默认环境为 pre，如果未指定的话

# 根据环境设置端口和其他参数
if [ "$ENVIRONMENT" == "prod" ]; then
    APP_PORT=7001          # 生产环境的应用端口
    JAVA_OPTS=""           # 生产环境不带 -Dspring.profile.active
    APP_HOME=/home/admin/prod/application # 从package.tgz中解压出来的jar包放到这个目录下
else
    APP_PORT=7002          # 非生产环境的应用端口
    JAVA_OPTS="-Dspring.profile.active=${ENVIRONMENT}"  # 非生产环境带 -Dspring.profile.active
    APP_HOME=/home/admin/application # 从package.tgz中解压出来的jar包放到这个目录下
fi

APP_START_TIMEOUT=60    # 等待应用启动的时间
HEALTH_CHECK_URL=http://127.0.0.1:${APP_PORT}  # 应用健康检查URL
JAR_NAME=${APP_HOME}/target/${APP_NAME}-${JAR_VERSION}.jar # jar包的名字
JAVA_OUT=${APP_HOME}/logs/start.log  #应用的启动日志

# 创建出相关目录
mkdir -p ${APP_HOME}
mkdir -p ${APP_HOME}/logs

usage() {
    echo "Usage: $PROG_NAME {start|stop|restart} [env]"
    exit 2
}

health_check() {
    exptime=0
    echo "checking ${HEALTH_CHECK_URL}"
    while true
        do
            status_code=`/usr/bin/curl -L -o /dev/null --connect-timeout 5 -s -w %{http_code}  ${HEALTH_CHECK_URL}`
            if [ "$?" != "0" ]; then
               echo -n -e "\rapplication not started"
            else
                echo "code is $status_code"
                if [ "$status_code" == "200" ];then
                    break
                fi
            fi
            sleep 1
            ((exptime++))

            echo -e "\rWait app to pass health check: $exptime..."

            if [ $exptime -gt ${APP_START_TIMEOUT} ]; then
                echo 'app start failed'
               exit 1
            fi
        done
    echo "check ${HEALTH_CHECK_URL} success"
}

start_application() {
    echo "starting java process with environment: $ENVIRONMENT"
    nohup java $JAVA_OPTS -jar ${JAR_NAME} > ${JAVA_OUT} 2>&1 &
    echo "started java process"
}

stop_application() {
   checkjavapid=`ps -ef | grep java | grep ${APP_NAME} | grep pre | grep -v grep |grep -v 'deploy.sh'| awk '{print$2}'`

   if [[ ! $checkjavapid ]];then
      echo -e "\rno java process"
      return
   fi

   echo "stop java process"
   times=60
   for e in $(seq 60)
   do
        sleep 1
        COSTTIME=$(($times - $e ))
        checkjavapid=`ps -ef | grep java | grep ${APP_NAME} | grep pre | grep -v grep |grep -v 'deploy.sh'| awk '{print$2}'`
        if [[ $checkjavapid ]];then
            kill -9 $checkjavapid
            echo -e  "\r        -- stopping java lasts `expr $COSTTIME` seconds."
        else
            echo -e "\rjava process has exited"
            break;
        fi
   done
   echo ""
}

start() {
    start_application
    health_check
}

stop() {
    stop_application
}

case "$ACTION" in
    start)
        start
    ;;
    stop)
        stop
    ;;
    restart)
        stop
        start
    ;;
    *)
        usage
    ;;
esac