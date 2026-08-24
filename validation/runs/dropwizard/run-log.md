# dropwizard - corpus phase run log

Repo: https://github.com/dropwizard/dropwizard.git @ 87940b9728fa6cce6598a0433da97ace373ee828
Modules:
  - dropwizard-util = dropwizard-util
  - dropwizard-validation = dropwizard-validation
Build tool: Maven
JaCoCo: 0.8.14 (bound via CLI goals)
Surefire excludes: 
pom.xml patches, applied to every module listed above (applied to the throwaway clone only - never committed to the coverdict repo):
(none)

Commands (reactor-relative, run from C:\Users\Mert\Desktop\coverdict-corpus\dropwizard):
  mvn -B -pl dropwizard-util,dropwizard-validation -am clean org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.14:report
Language level: 11

  java -jar coverdict.jar analyze --repo . --base 87940b9728fa6cce6598a0433da97ace373ee828~50 --module dropwizard-util=dropwizard-util --module dropwizard-validation=dropwizard-validation --source-roots dropwizard-util=dropwizard-util/src/main/java --source-roots dropwizard-validation=dropwizard-validation/src/main/java --test-roots dropwizard-util=dropwizard-util/src/test/java --test-roots dropwizard-validation=dropwizard-validation/src/test/java --report dropwizard-util=dropwizard-util/target/site/jacoco/jacoco.xml --report dropwizard-validation=dropwizard-validation/target/site/jacoco/jacoco.xml --language-level 11 --out verdict-base.json
  java -jar coverdict.jar analyze --repo . --no-vcs --module dropwizard-util=dropwizard-util --module dropwizard-validation=dropwizard-validation --source-roots dropwizard-util=dropwizard-util/src/main/java --source-roots dropwizard-validation=dropwizard-validation/src/main/java --test-roots dropwizard-util=dropwizard-util/src/test/java --test-roots dropwizard-validation=dropwizard-validation/src/test/java --report dropwizard-util=dropwizard-util/target/site/jacoco/jacoco.xml --report dropwizard-validation=dropwizard-validation/target/site/jacoco/jacoco.xml --language-level 11 --out verdict-no-vcs.json
