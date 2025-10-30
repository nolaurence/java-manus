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
JAVA_OUT=/app/logs/worker_java.log  #应用的启动日志

CHROME_OUT=/app/logs/worker_chrome.log  # Chrome 的启动日志

# socat 端口转发
#socat TCP-LISTEN:9222,bind=0.0.0.0,fork,reuseaddr TCP:127.0.0.1:8222 &
#echo "socat started"
#sleep 2
#
## 启动 x11vnc
#x11vnc -display :1 -nopw -listen 0.0.0.0 -xkb -forever -rfbport 5900 &
#echo "x11vnc started"
#sleep 3
#
## 启动 websockify
#websockify --web=/usr/share/novnc 0.0.0.0:5901 localhost:5900 &
#echo "websockify started"
#sleep 3
# 启动xvfb虚拟显示服务器
rm -f /tmp/.X1-lock
Xvfb :1 -screen 0 1280x1024x24 &
echo "Xvfb started"
export DISPLAY=:1

sleep 2
echo "Xvfb started"

# 启动x11VNC 方便调试
x11vnc -display :1 -nopw -shared -listen 0.0.0.0 -xkb -forever -rfbport 5900 &

# 启动websockify
websockify 0.0.0.0:5902 localhost:5900 &

# 安装 playwright-mcp
npm i @playwright/mcp -g

# 启动 chromium 浏览器
#chromium \
#    --display=:1 \
#    --window-size=1280,1029 \
#    --start-maximized \
#    --no-sandbox \
#    --disable-dev-shm-usage \
#    --disable-setuid-sandbox \
#    --disable-accelerated-2d-canvas \
#    --disable-gpu \
#    --disable-features=WelcomeExperience,SigninPromo \
#    --no-first-run \
#    --no-default-browser-check \
#    --disable-infobars \
#    --test-type \
#    --disable-popup-blocking \
#    --disable-gpu-sandbox \
#    --no-xshm \
#    --new-window=false \
#    --disable-notifications \
#    --disable-extensions \
#    --disable-component-extensions-with-background-pages \
#    --disable-popup-blocking \
#    --disable-prompt-on-repost \
#    --disable-dialogs \
#    --disable-modal-dialogs \
#    --disable-web-security \
#    --disable-site-isolation-trials \
#    --remote-debugging-address=0.0.0.0 \
#    --remote-debugging-port=8222 > ${CHROME_OUT} 2>&1 &

# 启动 Java 应用
mkdir -p /app/logs
#sudo -u ubuntu java -jar /app/target/case-management-backend-0.0.1-SNAPSHOT.jar >> /app/logs/java.log 2>&1 &
# shellcheck disable=SC2024
sudo env PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 nohup java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar ${JAR_NAME} --spring.profiles.active=worker > ${JAVA_OUT} 2>&1 &
#sudo env PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 nohup java $JAVA_OPTS -jar ${JAR_NAME} --spring.profiles.active=worker > ${JAVA_OUT} 2>&1 &

echo "Java app started"

# 等待所有后台进程
wait