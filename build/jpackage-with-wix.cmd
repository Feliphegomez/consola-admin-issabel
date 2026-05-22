@echo off
setlocal
REM WiX path is passed by Maven (WIX_TOOLSET_HOME). wix311-binaries.zip extracts candle.exe at root (not under bin\).
if not defined WIX_TOOLSET_HOME set "WIX_TOOLSET_HOME=%~dp0..\target\wix-toolset-3.11"
set "WIX_ROOT=%WIX_TOOLSET_HOME%"
if not exist "%WIX_ROOT%\candle.exe" (
  echo ERROR: WiX not found at "%WIX_ROOT%\candle.exe"
  echo Run: mvn verify -P"jpackage,jpackage-installer" so the build downloads WiX first.
  exit /b 1
)
set "PATH=%WIX_ROOT%;%PATH%"
set "WIX=%WIX_ROOT%"
if not defined JAVA_HOME (
  echo ERROR: JAVA_HOME is not set. Use a JDK 21+ environment where JAVA_HOME points to the JDK root.
  exit /b 1
)
"%JAVA_HOME%\bin\jpackage.exe" %*
exit /b %ERRORLEVEL%
