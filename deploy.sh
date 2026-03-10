cd /home/nolaurence/dev/java-manus/frontend
rm -rf ./dist/*
tyarn run build

rm -rf ../backend/src/main/resources/static/*
cp -r ./dist/* /home/nolaurence/dev/java-manus/backend/src/main/resources/static

cd /home/nolaurence/dev/java-manus/backend
mvn clean package
