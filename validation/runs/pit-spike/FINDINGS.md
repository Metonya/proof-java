# M2 Faz 0 — PIT doğrulama deneyi sonuçları

Sorulan tek soru: **PIT makine-okunabilir, test-METODU düzeyinde bir
satır→testler haritası veriyor mu?** Sonuç: **evet, ama iddia edilen
mekanizma üzerinden değil.**

## 1. `linecoverage.xml`'in gerçek şeması, rapordakinden farklı

`JVM Per-Test Coverage Research.md` raporunun B1 bölümü şu şemayı iddia
ediyordu:

```xml
<coverage>
  <class name="com.example.OrderService">
    <method name="calculateTotal" desc="(I)V">
      <line number="42">
        <tests>
          <test name="com.example.OrderServiceTest.testCalculateTotal"/>
```

Gerçek çıktı (coverdict'in kendi reposunda üretildi,
`coverdict-repo/linecoverage-sample.xml`'de tam örnek):

```xml
<coverage>
<block classname='dev.coverdict.analysis.oracle.NullCheckOnlyRule'
       method='evaluate(...)Ljava/util/Optional;' number='2'><tests>
<test name='dev.coverdict.analysis.oracle.CustomOraclesTest.[engine:junit-jupiter]/[class:...]/[method:aGlobInTheMethodPartMatches()]'/>
```

Fark kritik: `number` alanı **satır numarası değil**, PIT'in kendi içsel
basic-block indeksi. `<line>` elemanı yok. `linecoverage.xml` tek başına
satır düzeyinde bir harita **vermiyor** — sadece blok→testler.

Test adları ise doğru: tam JUnit5 `UniqueId` formatında
(`[engine:...]/[class:...]/[method:...()]`), yani **metot düzeyinde**
(A3'ün granularity collapse endişesi bu eksende çözülmüş durumda).

## 2. Block→satır eşlemesi PIT'in içinde var, ama farklı bir kapıdan

`mutations.xml` her mutant için gerçek `<lineNumber>` veriyor, ama sadece
mutasyona uğrayan satırlar için — tüm satırlar için değil.

PIT'in kendi kaynak kodunda (`org.pitest:pitest:1.15.8`) gerçek block→satır
çözücü mevcut:

- `org.pitest.coverage.LineMap` arayüzü:
  `Map<BlockLocation, Set<Integer>> mapLines(ClassName)`
- `org.pitest.coverage.analysis.LineMapper` — gerçek implementasyon, sınıfın
  bytecode'unu (`ClassByteArraySource` üzerinden) yeniden analiz ederek
  block→satır haritasını çıkarıyor.
- `org.pitest.coverage.CoverageExporterFactory` — **doğrulanmış, gerçek bir
  SPI uzantı noktası** (`org.pitest.plugin.ToolClasspathPlugin`,
  `META-INF/services/org.pitest.coverage.CoverageExporterFactory` ile
  keşfediliyor; raporun iddia ettiği "1.15.4'te tanıtıldı" detayı
  doğrulanamadı ama sınıfın kendisi `pitest-entry-1.15.8.jar` içinde gerçek
  ve çalışıyor).

## 3. PoC: gerçek satır→testler haritası üretildi

`exporter-poc/CoverdictLineExporter.java` — `CoverageExporterFactory` +
`LineMapper` kullanan, ~150 satırlık bir PoC. `recordCoverage()`'da her
blok için `LineMapper.mapLines()` çağırıp satır numaralarını çözüyor,
`class#method -> satır -> [testler]` JSON'u yazıyor.

**Sonuç: coverdict'in kendi reposunda 1996/1996 blok satır numarasına
başarıyla çözüldü** (`coverdict-repo/pit-run.log`,
`[coverdictspike] totalBlocks=1996 matchedBlocks=1996
classesWithEmptyLineMap=0`).

Üretilen `coverdict-line-tests-sample.json`'dan (tam çıktı 213 sınıf/metot,
909 satır kaydı — burada ilk 5'i saklandı) doğrulama örneği
(`NullCheckOnlyRule.java`, gerçek kaynakla elle karşılaştırıldı):

| Satır | Kaynak | Test sayısı |
|---|---|---|
| 25 | `if (traversal.oracles().isEmpty()) {` | 81 |
| 26 | `return Optional.empty();` (iç blok) | 38 |
| 28 | ikinci koşul kontrolü | 36 |
| 29 | `isNullCheck` lambda'sı | 18 |

Kontrol akışıyla tam tutarlı: guard satırı en yüksek çokluğa sahip, iç dallar
daha az teste düşüyor.

**Cross-repo doğrulama (gson, `com.google.gson.internal.LazilyParsedNumber`):**
JUnit4 test adları da doğru çözüldü (`ClassName.methodName(ClassName)`
formatı), gerçek satır numaralarıyla (`gson/coverdict-line-tests.json`).

### Yan bulgu: block→satır çok-a-bir, dedup gerekiyor

gson çıktısında bazı satırlarda aynı test adı iki kez görünüyor (satır
116/117: `testCompareTo` ve `testEquals` ikişer kez). Sebep: birden fazla
blok aynı kaynak satırına düşebiliyor, PoC'nin `List` birleştirmesi
tekilleştirmiyor. **Faz 1 tasarımına not:** gerçek implementasyon satır
başına test kümesini `Set`, `List` değil, toplamalı — yoksa çokluk sayımı
(Q1) yanlış şişer.

## 4. Ölçülen süreler

| Repo | Kapsam fazı | Not |
|---|---|---|
| coverdict (41 test sınıfı, `analysis.oracle.*`) | ~1 saniye | `run12-fixed2.log` |
| gson (`internal.LazilyParsedNumber`, 1 test sınıfı) | <1 saniye | `gson-run4.log` |

Raporun "5000 test → 25 dakika" modeli **suite-genelinde** JaCoCo
sıralı-reset'i varsayıyordu. PIT'in kendi kapsam fazı çok daha hızlı çünkü
tek bir minion JVM'de bellek-içi probe reset yapıyor, disk I/O veya süreç
başlatma yok. Büyük repoda (assertj/junit-framework/dropwizard, yüzlerce
test sınıfı) tam suite ölçümü bu spike'a dahil edilmedi — Faz 2'ye kaldı.

## 5. Karşılaşılan ve çözülen ortam engelleri (kayıt için)

1. **JDK 25 gölgeliyor, JDK 17 gerekiyor.** PIT 1.15.8'in ASM sürümü class
   file major version 69 (Java 25) desteklemiyor:
   `IllegalArgumentException: Unsupported class file major version 69`.
   `windows-dev-environment` hafıza notuyla tutarlı; PATH'e Windows-stili
   sürücü harfi (`C:/...`) koymak bash'te `:`'yi ayraç sanıp PATH'i
   bozuyor — `/c/...` formatı gerekli.
2. **`pitest-junit5-plugin` geçişli `junit-platform-launcher:1.9.2`
   getiriyor**, projenin `1.12.2`'sini gölgeliyor,
   `OutputDirectoryProvider` eksikliğiyle test keşfi patlıyor. Çözüm: plugin
   bağımlılıklarına `junit-platform-launcher:1.12.2`'yi açıkça, önce
   listelemek.
3. **`CoverdictLineExporter.recordCoverage()` ana Maven sürecinde çalışıyor,
   minion'da değil.** `Thread.currentThread().getContextClassLoader()` SUT
   sınıflarını bulamıyor (boş harita, tüm 1996 blok eşleşmesiz). Çözüm:
   `.class` dosyalarını `target/classes` / `target/test-classes`'tan
   doğrudan dosya sisteminden okumak (`-Dcoverdictspike.classDirs=...`).
4. **`AllowlistCoverageGapsTest.mockitoInOrderVerifyIsRecognizedAsAnOracle`
   normal surefire'da geçiyor, PIT'in kendi JUnit5 motorunda kırılıyor.**
   Kök neden araştırılmadı (zaman bütçesi dışı) — Faz 2'nin sıra-bağımlılık
   deneyleri için ilgi çekici bir aday.
5. **gson'ın kendi `<argLine>--illegal-access=deny ${argLine}</argLine>`'ı**
   PIT'in minion'ında Maven property expansion olmadan kopyalanıyor,
   `Could not find or load main class ${argLine}` ile çöküyor. Bilinen
   corpus-özel pom kısıtı (`gson/run-log.md`'deki JaCoCo argLine notuyla aynı
   kökten), PIT'e özgü yeni bir tezahür.

## 6. Faz 0 kararı

**PIT dalı onaylandı (D-47).** Kill kriteri karşılandı: metot düzeyinde
harita + gerçek satır numarası + iki farklı repoda üretilebiliyor. Yedek dal
(Faz 0b, diff-kapsamlı JaCoCo sequential+reset) gerekmiyor.

Gerçek entegrasyon (Faz 4), raporun varsaydığı gibi `linecoverage.xml`'i
parse etmek **değil** — `CoverageExporterFactory` SPI'ını implemente eden
küçük bir jar'ı PIT'in tool classpath'ine eklemek. Bu, D-02'nin "asla kendi
motorunu yazma" ilkesiyle hâlâ uyumlu (bytecode enstrümantasyonu yazmıyoruz,
PIT'in kendisininkini kullanıyoruz) ama saf dosya-parse etmekten biraz daha
invaziv — bu fark D-47'de açıkça not edilmeli.

assertj, junit-framework, dropwizard'da tekrarı ve üç kararlılık deneyi
(Faz 2) yapılmadı; sonraki oturumun işi.

## 7. Faz 2a — çağırma modeli: `EntryPoint`, pom.xml'e hiç dokunmadan

Faz 0'ın açık bıraktığı soru: "hedef repoya bağımlılık eklemek gerekiyor
mu?" Cevap **hayır** — ama Faz 0'ın kendi spike'ı bunu görünmez kılmıştı,
çünkü geçici bir `pom.xml` profiliyle çalışıyordu.

`org.pitest.mutationtest.tooling.EntryPoint.execute(File, ReportOptions,
PluginServices, Map)` — `pitest-entry:1.15.8`'de gerçek, public, çalışan bir
API. `ReportOptions`'ın setter'ları (`setClassPathElements`, `setCodePaths`,
`setSourceDirs`, `setTargetClasses`, `setTargetTests`, `setReportDir`)
coverdict'in zaten topladığı `--classpath`/`--module`/`--source-roots`/
`--test-roots` bilgisinden doğrudan besleniyor.

**Kanıt (`entrypoint-poc/Faz2aSpike.java`):** coverdict'in kendi reposunda,
`coverdict-cli/pom.xml`'e **hiç dokunmadan**, kapsam fazı iki kez başarıyla
koştu (1402/1402 blok satıra çözüldü her ikisinde de). `git status` koşum
öncesi/sonrası tamamen boş kaldı.

### Yol boyunca bulunan iki tuzak (PIT'in kendi dokümantasyonunda yok)

1. **`setGroupConfig()` zorunlu.** Ayarlanmazsa `createMinionSettings()`
   `NullPointerException` fırlatıyor (`Objects.requireNonNull`). Maven
   plugin bunu sessizce varsayılan olarak ayarlıyor, programatik çağıran
   kendisi ayarlamak zorunda: `TestGroupConfig.emptyConfig()`.
2. **Path ayırıcıları karışık olamaz.** `moduleRoot + "\\target\\classes"`
   gibi `/` ve `\` karışık bir string, hatasız kabul ediliyor ama mutasyon
   ön-taraması sessizce **0 birim** buluyor — hiçbir hata mesajı yok. PIT'in
   classpath eşleştirmesi ayırıcıları normalize etmiyor.
   `File.getCanonicalPath()` ile düzeltildi. Bu, kendi başına yarım saatlik
   bir teşhis sürecine mal oldu — gelecekte aynı hataya düşmemek için
   kaydediliyor.

### Kapsam dışı kalan

Mutasyon fazı (kapsam fazından sonraki adım) `MINION_DIED` ile çöktü —
`useClasspathJar`/geçici jar yolu ile ilgili görünüyor. **L2'nin sorusuyla
ilgisi yok** (kapsam fazı zaten tamamlanmış ve doğru çıktıyı üretmişti);
araştırılmadı, mutasyon entegrasyonu (L3/M5) zamanı gelince ele alınacak.

**Doğrulanmadı:** Gradle hedefinde (junit-framework) aynı çağırma modeli.
Maven'ın `dependency:build-classpath`'i burada kullanıldı; Gradle'ın
eşdeğeri (`gradle dependencies` / `--write-locks` çıktısından classpath
çıkarma) test edilmedi — Faz 2b'nin ilk engeli bu olacak.

D-51 bu bulguları karar olarak kaydediyor.

## 8. Faz 2b–2e — assertj-core üzerinde dört kanıt kapısı

Hedef kapsam: `org.assertj.core.api.*` (215 üretim sınıfı, 164 test sınıfı —
tam repo değil, ROADMAP'in "ölçek" amacına yeten gerçek bir dilim).
Çağırma modeli Faz 2a'nınkiyle aynı (`EntryPoint`, hedef repo'nun pom'una
dokunulmadı — assertj-core'un kendi `jacoco.skip=false` yaması önceki bir
oturumdan zaten mevcuttu, bu spike'a özgü değil).

### Faz 2b — ölçek (D-18'in yerine geçen gerçek sayı)

**Kapsam fazı: 20–29 saniye** (üç ayrı koşum, `pit-run.log` dosyalarında),
17.039 sınıf#metot, 39.923 satır kaydı. D-18'in "5000 test → 25 dakika"
modeli **suite-genelinde** JaCoCo sıralı-reset'i varsayıyordu — bu sayı
onun yerini alıyor: diff-kapsamlı bir dilimde (tüm assertj-core'un ~%20'si)
PIT'in kapsam fazı saniyeler içinde bitiyor.

⚠️ Mutasyon fazı (kapsam fazından sonraki adım, L2'nin sorusuyla ilgisiz)
187 birim üretti ve 5+ dakika içinde bitmedi — `useClasspathJar` veya geçici
jar yoluyla ilgili bir `MINION_DIED` riski taşıyor. Araştırılmadı, L3/M5
zamanı gelince ele alınacak. Faz 2b–2e'nin tamamı sadece kapsam fazının
çıktısını (`coverdict-line-tests.json`, mutasyon başlamadan önce yazılıyor)
kullandı.

### Faz 2c — determinizm: GEÇTİ

İki bağımsız koşum, aynı komut. **39.923 kaydın hepsi birebir aynı**
(küme karşılaştırması: ortak anahtar 39.923, sadece-run1 0, sadece-run2 0,
ortalama Jaccard **1.0**, uyuşmazlık 0). ROADMAP'in kapısı (J=1.0) tam
olarak karşılandı.

### Faz 2d — çapraz motor uyumu: tanı (eşik aşıldı, açıklandı)

Önceki oturumdan kalan tam-suite JaCoCo raporuyla (`target/site/jacoco/
jacoco.xml`, phase-3 sonar-parity çalışmasından) karşılaştırıldı.

**İlk deneme hatalıydı** — JaCoCo'yu `(paket, satır)` ile anahtarlamıştım;
bir pakette onlarca `.java` dosyası olduğu için satır numaraları çakıştı,
%6.6 sahte uyuşmazlık üretti. `(dış sınıf, satır)` ile düzeltilince:

- 39.923 PIT kaydından **31.417'sinin JaCoCo'da hiç karşılığı yok** —
  beklenen: PIT hedef kapsamı hem üretim hem **test** sınıflarını
  kapsıyordu (`targetClasses = targetTests = org.assertj.core.api.*`),
  JaCoCo raporu ise standart `maven-jacoco-plugin` davranışıyla sadece
  `src/main/java`'yı raporluyor. Motor farkı değil, kapsam farkı.
- Kalan **8.506 karşılaştırılabilir kayıttan 80'i (%0.94) uyuşmuyor**
  (PIT "kapsandı" diyor, tam-suite JaCoCo "kapsanmadı" diyor) — eşiğin
  (%5) altında, tırmandırma gerekmedi.
- Yapısı incelendi: örneğin `AbstractClassAssert#isFinal` satır 427
  (`public SELF isFinal() {`) PIT'te kapsanmış görünüyor ama gerçek çalışma
  428–430'daki lambda içinde. Tek-ifadeli metot gövdelerinde PIT'in
  `LineMapper`'ı ile JaCoCo'nun bytecode-satır tablosu farklı satırı
  "asıl" kabul ediyor — iki aracın da ASM tabanlı ama bağımsız
  line-number-table okuma sezgisi var. Kusur değil, granülerlik farkı.

### Faz 2e — ablasyon: GEÇTİ (3/3, %100)

PIT'in çokluk=1 dediği, üretim koduna düşen 3 kayıt seçildi (test sınıfının
kendi satırları değil — o zaten tautolojik). Her biri tam olarak tek
`@Test` içeren bir test sınıfına aitti (`grep -c "@Test" ... == 1`), yani
sınıf-düzeyi `excludedTestClasses` ile o testi tam olarak dışlamak mümkündü:

| Üretim satırı | Tek test | Ablasyon sonrası |
|---|---|---|
| `ComparatorBasedComparisonStrategy#iterableContains:99` | `ObjectArrayAssert_usingComparatorForType_Test` | kapsamsız ✓ |
| `AbstractIntArrayAssert#isEmpty:58` | `IntArrayAssert_isEmpty_Test` | kapsamsız ✓ |
| `AbstractCharArrayAssert#isEmpty:59` | `CharArrayAssert_isEmpty_Test` | kapsamsız ✓ |

Üçü de testi çıkarınca satır **tamamen kayboldu** (JSON'da anahtar yok) —
"bu satırı sadece bu test çalıştırıyor" iddiası nedensel olarak doğrulandı,
sadece öz-tutarlı değil.

### Kapsam dışı kalan

junit-framework (Gradle) ve dropwizard (çok modüllü) hiç denenmedi —
D-51'in "doğrulanmadı" notu hâlâ geçerli. Faz 2b–2e'nin dört kapısı tek
repoda (assertj) geçti; ROADMAP'in "her repo kendi şeklini kesebilir"
maddesi gereği bu, L2'yi genel olarak onaylamaya yetmez — iki repo daha
gerekiyor.

## 9. Faz 2b–2e — dropwizard (çok modüllü): dört kapı, biri kısmi

Hedef: `dropwizard-util` + `dropwizard-validation` — daha önceki çok-modüllü
corpus çalışmasında bağlanan aynı iki modül (`validation/runs/dropwizard/
run-log.md`). Çağırma modeli assertj'deki `Faz2aSpike`'ın çok-modüllü
varyantı: `Faz2bMultiModuleSpike.java`, iki modülün `target/classes`,
`target/test-classes`, kaynak dizinleri ve bağımlılık classpath'lerini **tek
`ReportOptions`'ta birleştiriyor**. PIT'in Maven modülü kavramı yok — sadece
classpath/kaynak-dizini listeleri görüyor, çok-modüllü hedef "birleşimi ver"
den ibaret.

**Kanıt:** çıktıda hem `io.dropwizard.util.*`'tan 18 sınıf hem
`io.dropwizard.validation.*`'tan 43 sınıf gerçekten var (326 sınıf#metot,
2241 satır kaydı) — birleşim sahte değil, gerçekten iki modülden geliyor.
`git status` iki modülde de koşum öncesi/sonrası boş kaldı.

### Faz 2b — ölçek: 2–3 saniye

Küçük modül çiftinde beklenen sonuç; assertj'nin 20 saniyesiyle
karşılaştırıldığında kapsam fazının maliyetinin hedef büyüklüğüyle
doğrusal ölçeklendiğini destekliyor.

### Faz 2c — determinizm: KISMİ (2240/2241, ortalama J = 0.9998)

**Bu, üç repoda ilk kez tam geçmeyen kapı — gizlenmeyecek.** Tek
uyuşmazlık: `SelfValidatingValidatorTest#hasSignature:95`, iki koşumda
farklı 3'lü test kümesi (4 olası testten).

Kök neden kaynakta bulundu
(`SelfValidatingValidatorTest.java:80-95`): `getMethod()` yardımcı metodu
`annotatedType.getMemberMethods()` üzerinde dolaşıyor — bu, JDK'nın
reflection metot listesi üzerine kurulu, **sıralaması garantili olmayan**
bir koleksiyon. `hasSignature`'ın 95. satırı her aday metot için (eşleşen
metot bulununcaya kadar) çalışıyor; hangi adayların kontrol edildiği
koşumdan koşuma JVM'in metot numaralandırma sırasına bağlı olarak
değişiyor.

**Bu PIT'in kusuru değil, SUT'un kendi test yardımcı kodunun reflection
kullanımı.** Coverdict'in ürün açısından sonucu: gerçek kullanıcı
repolarında bu sınıf bir kaynak — parmak izi tek bir satırda (0.045%)
küçük ama gerçek bir belirsizlik taşıyabilir. D-52 (aşağıda) bunu kaydediyor.

### Faz 2d — çapraz motor: %0 uyuşmazlık

416 karşılaştırılabilir kayıttan (2241 - 1825 JaCoCo'da karşılığı olmayan,
assertj'deki gibi test-sınıfı kapsaması nedeniyle) **0 uyuşmazlık**.
assertj'nin %0.94'ünden bile temiz — küçük, basit modüllerde lambda/tek
satır granülerlik farkı daha az fırsat buluyor.

### Faz 2e — ablasyon: GEÇTİ (3/3, %100)

Aynı desen: çokluk=1 kayıtlar, tek testi barındıran sınıfın tamamı hariç
tutuldu (bu sefer testin ait olduğu sınıfta başka @Test'ler olsa bile
geçerli — çünkü PIT verisi zaten o satıra sınıftaki başka hiçbir testin
dokunmadığını söylüyor, aksi hâlde çokluk 1 olmazdı):

| Üretim satırı | Modül | Ablasyon sonrası |
|---|---|---|
| `DataSize#getQuantity:240` | util | kapsamsız ✓ |
| `Duration#getUnit:171` | util | kapsamsız ✓ |
| `MinDurationValidator#isValid:28` | validation | kapsamsız ✓ |

### Sonuç

3/4 repo (coverdict, assertj, dropwizard) dört kapının en az üçünü tam
geçti; dropwizard'ın determinizm kapısı **%99.96** ile neredeyse geçti,
kök nedeni SUT'un reflection kullanımına ait olarak izole edildi — L2
motorunun kendi hatası değil.

## 10. junit-framework (Gradle) — çağırma modeli çalıştı, kapsam fazı gerçek bir bytecode kısıtına takıldı

### Çağırma modeli kısmı: sorun yok

Gradle projeleri için `mvn dependency:build-classpath` eşdeğeri yok;
`--init-script` ile (repo'nun kendi dosyalarına dokunmadan, harici bir
Gradle mekanizmasıyla) test runtime classpath'i çıkaran bir script yazıldı.
İki gerçek Gradle-özel engel çıktı ve çözüldü:

1. **Isolated Projects modu** (`org.gradle.unsafe.isolated-projects`, bu
   repoda etkin) `allprojects{}`/`subprojects{}` ile çapraz-proje erişimini
   reddediyor — `gradle.beforeProject { }` + o projenin kendi
   `afterEvaluate { }`'i içinde görev kaydı ile çözüldü.
2. **Configuration Cache** (Isolated Projects onu zorunlu kılıyor,
   devre dışı bırakılamıyor) `doLast` içinde `Project`/`extensions`
   referansı tutmayı reddediyor — `FileCollection`'ı **konfigürasyon
   zamanında** yakalayıp `doLast`'a sadece o referansı taşımakla çözüldü.

Sonuç: `git status` koşum boyunca temiz kaldı — D-51'in "hedef repoya
dokunmadan" iddiası Gradle'da da doğrulandı, **kapsam fazı hiç
çalışmamış olsa bile**.

### Kapsam fazı: gerçek, çözülmemiş bir bytecode engeli

`junit-vintage-engine` modülünün **ana** kaynak kümesi Java 7 hedefiyle
derleniyor (major version 51 — kütüphanenin kendi geriye-uyumluluk
politikası). Ama **test** ve **testFixtures** kaynak kümelerinin hiçbir
`--release` kısıtı yok — Gradle daemon'ının kendi JDK'sıyla (bu makinede
JDK 25) derleniyorlar, **major version 69**. PIT 1.15.8'in ASM'i bunu
okuyamıyor (`IllegalArgumentException: Unsupported class file major
version 69`) — Faz 0'da coverdict'in kendi derlemesinde çözdüğümüz sorunun
aynısı, ama bu sefer sorun bizim toolchain'imizde değil, **hedef repo'nun
kendi test kodunda**.

**Denenen düzeltme:** hedef repo'ya dokunmadan, sadece test/testFixtures
kaynaklarını kendi `javac --release 21`'imizle ayrı bir dizine derleyip
PIT'i oraya yöneltmek (D-51'in ruhuna uygun — okuma var, repo'ya yazma
yok). İşe yaradı ama **kartopu gibi büyüdü**: `junit-vintage-engine`'in
kendi testFixtures'ı derlendi, ama test kaynakları `TrackLogRecords`,
`DisabledInEclipse`, `assertPreconditionViolationNotEmptyFor` gibi
**başka modüllerin** (`junit-platform-commons`, muhtemelen başkaları)
testFixtures'larına da ihtiyaç duyuyor — onlar da aynı JDK25 sorununu
taşıyor. Bir modülü kurtarmak için art arda başka modülleri kurtarmak
gerekiyor; bu noktada durduruldu.

### Sonuç: kapsam dışı değil, gerçek bir sınır

Bu, "denenmedi" değil — **denendi, çağırma modeli sorunsuz çalıştı,
kapsam fazı belgelenmiş, gerçek bir teknik sınıra takıldı.** ROADMAP'in
kill kriteri tam olarak bunun için var: "L2 o repo'nun şekli için
kesilir." junit-framework'ün şekli — kütüphane kodu eski bir Java sürümü
hedefliyor ama test altyapısı build makinesinin JDK'sıyla (sabitlenmemiş,
`gradle-daemon-jvm.properties` neyi işaret ediyorsa) derleniyor — PIT
1.15.8 ile şu an **çalışmıyor**. D-53 bunu kaydediyor.

**Not, gelecekteki bir oturum için:** Bu sınır PIT'in ASM sürümüne özgü,
kalıcı değil. PIT'in daha yeni bir sürümü (ASM'in Java 25 desteği
eklenmiş bir sürümü) çıkarsa, ya da coverdict test/testFixtures
kaynaklarının **tamamını** (kaç modül olursa olsun) kendi `javac`'ıyla
`--release`'i main ile eşleştirerek yeniden derleyen genel bir mekanizma
kurarsa, bu engel kalkar. Şu an için M2'nin kapsamı dışında.

**Güncelleme (2026-08-25, D-54):** Bu not kısmen doğrulandı, kısmen
yanlış çıktı — "gelecekte daha yeni bir PIT" fikri bu oturumda gerçekten
denendi.

## 11. PIT 1.25.9 denemesi — ASM tavanı gerçekten kalkıyor, ama yeni bir sıfır-blok sorunu var

`org.pitest.reloc.asm.Opcodes` sabitleri doğrulandı: 1.15.8 `V22=66`'da
bitiyor, 1.25.9 `V25=69`'dan `V27=71`'e kadar taşıyor — yani §10'un
ASM parse engeli **gerçekten kalkmış**.

**JDK 17 sürücüsü + Java 25 bytecode (`pit-run-129-jdk17-trimmed.log`):**
Beklenen sonuç — JDK 17 runtime'ı Java 25 sınıflarını hiç yükleyemiyor
(`class file version 69.0 > 61.0`), bu PIT'le değil çalıştırma JDK'sıyla
ilgili, `Found 0 tests`. PIT'in kendisi burada hatasız.

**JDK 25 sürücüsü + Java 25 bytecode, gerçek test classpath'i
(`pit-run-129-jdk25-trimmed.log`):** ASM engeli yok, `Found 808 tests` —
test keşfi ve koşumu **gerçekten çalıştı**. Ama exporter
`totalBlocks=0 matchedBlocks=0 classesSeen=0` — kapsama ajanı hiçbir
blok kaydetmedi. Ne bir istisna ne bir hata mesajı var; sessizce boş.
Kök neden bu oturumda izole edilemedi.

**Sonuç:** D-53'ün "daha yeni bir PIT sürümü bu engeli kaldırabilir"
notu **kısmen doğru** — ASM tavanı gerçekten 1.25.9'da kalkıyor — ama
**yeni, farklı, çözülmemiş bir engelle** karşılaşıldı. Ayrıca 1.25.9,
`ReportOptions.setUseClasspathJar()`/`useClasspathJar()`'ı tamamen
kaldırmış ve `DefaultCoverageGenerator`'ın constructor'ına zorunlu bir
`TestStatListener` parametresi eklemiş — coverdict'in doğrudan çağırdığı
API'de gerçek, doğrulanmış bir kırılma, sıfır-blok sorunundan bağımsız.
D-54 bu bulguları karar olarak kaydediyor; M2/L2 için 1.15.8'de kalınıyor.
