@echo off
setlocal

rem Use UTF-8 console codepage so the demo's Chinese output renders correctly.
chcp 65001 >nul

rem The JVM does not follow chcp: it still encodes output with the system encoding
rem (GBK on Chinese Windows), which shows as garbled text on a UTF-8 console.
rem Force UTF-8 for both the Maven JVM and the forked Spring Boot application JVM.
set "MAVEN_OPTS=%MAVEN_OPTS% -Dfile.encoding=UTF-8"

rem Switch to the script directory so relative Maven paths work.
cd /d "%~dp0"

rem Connection settings: environment variables take precedence over defaults.
if "%WAYMARK_ENDPOINT%"=="" set WAYMARK_ENDPOINT=http://127.0.0.1:9868
if "%WAYMARK_USERNAME%"=="" set WAYMARK_USERNAME=admin
if "%WAYMARK_PASSWORD%"=="" set WAYMARK_PASSWORD=123456
if "%WAYMARK_NAMESPACE%"=="" set WAYMARK_NAMESPACE=public

echo ============================================================
echo  Spring Boot 3 example (auto config + hot reload + discovery)
echo  endpoint  = %WAYMARK_ENDPOINT%
echo  username  = %WAYMARK_USERNAME%
echo  namespace = %WAYMARK_NAMESPACE%
echo ============================================================
echo.

rem Install the core SDK and the Boot 3 starter into the local repository (needed once, safe to repeat).
call mvn -q -DskipTests -f "..\..\..\java\pom.xml" -pl waymark-spring-boot3-starter -am install

call mvn -q spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8"
set EXIT_CODE=%ERRORLEVEL%

echo.
echo ==== exited with code %EXIT_CODE% ====
pause
exit /b %EXIT_CODE%
