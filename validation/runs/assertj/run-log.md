# assertj - corpus phase run log

Repo: https://github.com/assertj/assertj.git @ 4c5ab4862668e769d0e72492f400bd919469455d
Module: assertj-core at assertj-core
JaCoCo: 0.8.15 (bound via CLI goals)
Surefire excludes: 
pom.xml patches (required on a fresh (non -SkipBuild) build - NOT (re)applied this run, this run reused the clone's existing state as-is):
  - "@${project.basedir}/argFile" -> "@${project.basedir}/argFile -Duser.language=en -Duser.country=US ${jacocoArgLine}"
  - "<jacoco.skip>true</jacoco.skip>" -> "<jacoco.skip>false</jacoco.skip>"
Language level: 17

Commands (module-relative, run from C:\Users\Mert\Desktop\coverdict-corpus\assertj):
  mvn -B -pl assertj-core -am clean org.jacoco:jacoco-maven-plugin:0.8.15:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.15:report
  java -jar coverdict.jar analyze --repo . --base 4c5ab4862668e769d0e72492f400bd919469455d~50 --module assertj-core=assertj-core --source-roots assertj-core=assertj-core/src/main/java --test-roots assertj-core=assertj-core/src/test/java --report assertj-core=assertj-core/target/site/jacoco/jacoco.xml --language-level 17 --out verdict-base.json
  java -jar coverdict.jar analyze --repo . --no-vcs (same module/source/test/report args) --out verdict-no-vcs.json
