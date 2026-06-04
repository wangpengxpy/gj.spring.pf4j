@echo off
echo Publish gj.spring.pf4j to Maven Central...
mvn deploy -pl src/gj-parent,src/gj-pf4j,src/gj-modelmapper,src/gj-archetypes -DskipTests
echo.
echo If successful, open https://central.sonatype.com to confirm publishing.
pause
