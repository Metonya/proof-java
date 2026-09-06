@echo off
rem Thin wrapper so the release is invoked as `proof-java <args>`, the same
rem shape as any other CLI, instead of `java -jar proof-java.jar <args>`.
rem Resolves the jar next to this script - the two ship together in the
rem same release archive and must stay side by side.
setlocal
set "DIR=%~dp0"
java -jar "%DIR%proof-java.jar" %*
