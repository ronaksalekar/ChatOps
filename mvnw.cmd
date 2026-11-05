@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------
@echo off

for %%i in ("%~dp0.") do set "MAVEN_PROJECTBASEDIR=%%~fi"
set MAVEN_WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set MAVEN_WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

for /F "usebackq tokens=1,2 delims==" %%A in (%MAVEN_WRAPPER_PROPERTIES%) do (
    if "%%A"=="distributionUrl" set DISTRIBUTION_URL=%%B
)

set JAVA_HOME_CANDIDATE=%JAVA_HOME%
if not defined JAVA_HOME_CANDIDATE (
    if exist "C:\Program Files\Java\jdk-25" set "JAVA_HOME=C:\Program Files\Java\jdk-25"
    if exist "C:\Program Files\Java\jdk-25" set "JAVA_HOME_CANDIDATE=C:\Program Files\Java\jdk-25"
)
if defined JAVA_HOME_CANDIDATE (
    set JAVA_CMD=%JAVA_HOME_CANDIDATE%\bin\java.exe
) else (
    set JAVA_CMD=java
)

"%JAVA_CMD%" -classpath %MAVEN_WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
