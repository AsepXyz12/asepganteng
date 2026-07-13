@echo off
set DIR=%~dp0
java -Dorg.gradle.appname="gradlew.bat" -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
