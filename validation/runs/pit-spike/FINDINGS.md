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
