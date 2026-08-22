# Java Test Quality Analyzer — geliştirme planı

Bu doküman, `demo-bank/` altındaki çalışan prototipten çıkan ölçümlere dayanır.
Spekülasyon değil, koşturulmuş sonuç. Prototipi `./run.sh` ile tekrar üretebilirsin.

---

## 1. Problem

AI ile kod geliştirmeye geçtikten sonra unit testler de AI ile yazılmaya başlandı.
Bunun ürettiği üç somut arıza var:

1. **Coverage yalan söylüyor.** `%80 coverage` hedefi, assertion'ı olmayan veya
   tautolojik assertion içeren testlerle kolayca tutturuluyor. Sayı yükseliyor,
   fault-detection kapasitesi yükselmiyor.
2. **Test suite şişiyor.** Aynı davranışı test eden 3-4 test yan yana duruyor.
   Pipeline süresi ve bakım maliyeti lineer artıyor, bilgi artmıyor.
3. **Geri bildirim çok geç geliyor.** New-code coverage'ı ancak CI'da Sonar
   koştuktan sonra görüyorsun. Geliştirici local'de "bu satır test edilmedi"
   bilgisine ulaşamıyor.

Bu üçü ayrı problemler ve ayrı çözüm katmanları gerektiriyor.

## 2. Mevcut durum — ne var, ne yok

### Yeniden yazılmayacaklar (hazır kullanılacak)

| Araç | Ne verir | Lisans |
|---|---|---|
| **JaCoCo** | Coverage motoru, exec + XML + HTML | EPL-2.0 |
| **PIT / pitest** | Mutation testing, `scmMutationCoverage` ile diff-scoped | Apache-2.0 |
| **Descartes** (`STAMP-project/pitest-descartes`) | Extreme mutation → **pseudo-tested method** tespiti | LGPL-3.0 |
| **diff-cover** | Coverage XML + git diff → new-code coverage, dil-agnostik | Apache-2.0 |
| **JavaParser** | Test kaynak kodu AST analizi | Apache-2.0 / LGPL |

Descartes en değerli hazır bileşen: "cover edilmiş ama dönüş değeri hiç kontrol
edilmemiş" metotları buluyor — yani `NO_ORACLE`'ın statik analizle
yakalanamayan, semantik versiyonu.

**Lisans notu:** Descartes LGPL-3.0. Apache-2.0 bir ürün planlanıyorsa link
edilmemeli, ayrı process olarak çağrılmalı.

### Kısmen çözülmüş

- **SonarQube S2699** — "Tests should include assertions". Ama cross-file analiz
  yapmıyor; custom assertion helper'ları olan takımlarda false-positive üretiyor
  ve rule parametrizasyonu sınırlı.
- **PMD `JUnitTestsShouldIncludeAssert`** — benzer kapsam, benzer sınırlar.

Yani "assertion yok" tespiti kısmen var. Katma değer, **assertion var ama işe
yaramıyor** vakalarında (tautoloji, catch içi oracle, sadece `assertNotNull`).

### Çözülmemiş

- **Redundant test tespiti** — Java'da production-ready açık kaynak araç yok.
  Akademide "Test Suite Minimization" olarak çalışılıyor (FAST-R, ATM, Nemo),
  prototipler yarı terk edilmiş.
- **Per-test coverage** — sadece **OpenClover**'da native var, ama source
  instrumentation gerektiriyor, Java 17 desteği "experimental", paralel test
  koşumunu desteklemiyor. Modern stack'e sokmak acı verici.
  **Teamscale Java Profiler** JaCoCo üstüne testwise coverage üretiyor ve açık
  kaynak, ama analiz tarafı kendi sunucusuna bağlı.

### Rekabet manzarası

GitHub `test-quality` topic'inde 20 repo var. Hepsi Python / TypeScript / Go /
Rust. **Java yok.** Neredeyse hepsi 0–5 yıldız, çoğu son bir yılda açılmış —
yani problem yeni fark ediliyor, kimse çözmemiş.

Kritik ayrım: bu araçların çoğu **LLM-judge** tabanlı (Pragma, prove-it,
DeepDive). Non-deterministik, API key istiyor, aynı kod için farklı sonuç
verebiliyor. Bizim yaklaşımımız **execution evidence** — JaCoCo + PIT. Aynı
girdi, aynı çıktı, sıfır maliyet, CI'da tekrar edilebilir.

## 3. Mimari

Dört katman. Her katman tek başına faydalı; üstteki alttakini gerektirmiyor.
Bu önemli, çünkü kullanıcı L3'ü hiç açmadan da değer alabilmeli.

| Katman | Tespit | Girdi | Maliyet |
|---|---|---|---|
| **L0 — Statik** | `NO_ORACLE`, `TAUTOLOGICAL_ORACLE`, `ORACLE_IN_CATCH`, `NULL_CHECK_ONLY` | Test kaynak kodu (AST) | saniyeler |
| **L1 — Coverage** | Overall + new-code coverage, uncovered satırlar | `jacoco.xml` + `git diff` | saniyeler |
| **L2 — Per-test** | `DUPLICATE`, `EAGER_TEST` | Test başına `.exec` | dakikalar |
| **L3 — Mutation** | Pseudo-tested method, gerçek oracle gücü | Descartes (diff-scoped) | dakikalar |

### L2 nasıl çalışıyor

JUnit 5 `TestExecutionListener`:

```
executionStarted(test)  -> RT.getAgent().reset()
executionFinished(test) -> RT.getAgent().getExecutionData(true) -> <testId>.exec
```

**Prototipte yakalanan tuzak:** `reset()` çağrısı test instance'ı
oluşturulduktan *sonra* yapılırsa, `private final Foo foo = new Foo();` gibi
field initializer'ların coverage'ı kaybolur. Prototipte bu, coverage'ı
%78.9 yerine %76.1 gösterdi. `reset()` instance oluşturmadan önce olmalı.

### Redundancy algoritması — en kritik tasarım kararı

İlk implementasyon şuydu: *"A'nın coverage'ı B'nin altkümesiyse A gereksizdir."*
Bu **yanlış** ve prototipte iyi testleri sildirmeye kalktı.

Doğru kural:

- **Identical coverage** → gerçek duplicate cluster.
  Oracle'ı en güçlü olanı tut, kalanları silme adayı işaretle.
  Confidence: silinecek olanın assertion'ları tutulanın altkümesiyse `HIGH`,
  değilse `MEDIUM`.
- **Strict subset (A ⊂ B)** → **A'ya dokunma.** Dar ve odaklı test iyi tasarımdır.
  Şüpheli olan B'dir. B'nin coverage'ı ≥2 odaklı testin *birleşimine* eşitse
  B `EAGER_TEST` olarak işaretlenir — ama silme adayı değil, "böl veya gözden
  geçir" adayı.

Bu ayrım olmadan araç güven kaybeder ve proje ölür. Literatürdeki
"coverage bilgisi çok false-positive üretir" uyarısının pratik karşılığı budur.

**Oracle gücü sıralaması** (hangisi tutulacak kararı için):
`NO_ORACLE` → 0, herhangi bir weak-oracle bulgusu → 1, temiz → `2 + assertion sayısı`.

## 4. Ölçüm sonuçları

### Coverage metriği: aynı veriden üç farklı sayı

| Mod | Sonuç | Tanım |
|---|---|---|
| `jacoco-line` | **78.9%** (56/71) | Satırda bir instruction çalıştıysa covered |
| `strict` | **56.3%** (40/71) | Partial satır covered sayılmaz |
| `sonar` | **72.8%** (91/125) | `(CT+CF+LC) / (2B+EL)` |

`jacoco-line` modu JaCoCo'nun kendi LINE sayacıyla birebir tuttu — parser doğrulandı.

**Sonar uyumluluğunun cevabı:** Sonar'ın `Coverage` metriği line ve condition'ın
harmanı. JaCoCo terimleriyle: `(cb + LC) / (cb + mb + EL)`.
Local JaCoCo yüzdenin Sonar'la tutmamasının sebebi versiyon farkı değil, **farklı
formül**. Sonar-uyumlu sayı vermek istiyorsan bu formülü implement etmek zorundasın.

Quality gate kurarken hangi tanımı konuştuğunu net söyle — %56 ile %79 arası
22 puanlık bir aralık var ve üçü de "doğru".

### Ölçeklenme: mimariyi belirleyen ölçüm

16 test, per-test analiz: **4959ms → test başına ~309ms.**

Sebep: her `.exec` dosyası için ayrı `jacococli` JVM'i açılıyor. JVM startup'ı
bunun ~250ms'i. 5000 testlik bir repoda bu **~25 dakika** eder — kabul edilemez.

**Sonuç: core motor JVM içinde, JaCoCo Java API ile çalışmalı** (tek process,
N exec dosyası analiz edilir; test başına maliyet ~1-5ms'e düşer, ~60x hızlanma).
Bu da aracı doğal olarak Maven/Gradle plugin yapar. Python + shell-out yaklaşımı
prototip için doğru, ürün için yanlış.

## 5. Ürün formu ve sıra

1. **CLI (tek jar).** `tqa analyze --report ... --base main`
   Her şeyin temeli. JSON üretir; diğer tüm yüzeyler o JSON'u tüketir.
   CLI olmadan ne IDE eklentisi ne CI botu besleyebilirsin.
2. **Maven / Gradle plugin.** Zaten JVM'desin; classpath, kaynak dizinleri ve
   multi-module yapı bedava gelir.
3. **VSCode / IntelliJ eklentisi.** Uncovered satırlar hem gutter'da hem panelde.
   CLI JSON şeması stabilleşmeden başlanmamalı.
4. **CI botu.** PR'a yorum: new-code coverage + yeni eklenen weak oracle'lar.
5. **Claude Code skill / MCP — en son.** "AI test yazar → tqa denetler → AI
   düzeltir" döngüsü çok mantıklı, ama araç olgunlaşmadan skill sarmak boşa emek.

### IntelliJ coverage'ından farkı

IntelliJ tek geliştiricinin makinesinde, tek çalıştırmada coverage gösterir.
Kendi runner'ında "tracking per test coverage" seçeneği bile var. Ama:

- sonucu paylaşılamaz, CI'a giremez
- git diff bilmez → new-code coverage yok
- redundancy analizi yok
- quality gate kurulamaz
- IntelliJ runner'ı ile JaCoCo runner'ı farklı sayılar üretir

IntelliJ *"ne cover edildi"* der. Bu araç *"hangi test silinebilir, hangi test
yalan söylüyor"* der. Farklı sorular.

## 6. Operasyonel konular

### Multi-module

Her modül kendi `jacoco.exec`'ini üretir. Plugin bunları aggregate eder;
CLI ise birden fazla `--report` kabul eder. Kritik nokta: **new-code coverage
repo kökündeki tek bir git diff'ten hesaplanmalı**, modül başına değil —
aksi halde modüller arası taşan değişikliklerde sayı bozulur.

### Exclusion yönetimi

Tek bir filtre katmanı, tüm metrikler ondan beslenir.

Prototipte yakalanan hata: exclusion listelere uygulanıyordu ama toplam yüzde
XML kökündeki sayaçtan alınıyordu — dışlama başlıktaki sayıyı hiç
değiştirmiyordu. Düzeltince %76.1 → %73.4 oldu.

Kural: **hiçbir metrik ham rapor sayaçlarından okunmamalı**, hepsi filtrelenmiş
veri setinden yeniden hesaplanmalı. Aksi halde başlık ile detay çelişir ve
kullanıcı araca güvenmez.

Config dosyası (`tqa.yml`) formatı, Sonar'ın `sonar.coverage.exclusions`
sözdizimiyle uyumlu glob'lar kullanmalı ki takımlar iki yerde iki farklı liste
tutmasın.

Tipik dışlanacaklar: DTO/POJO'lar, generated kod, `*Config.java`, mapper'lar.

**Not:** DTO getter/setter testleri (prototipteki `AccountDtoTest`) coverage +
assertion analiziyle yakalanamaz — gerçek assertion'ları var ve unique coverage
üretiyorlar. Bunlar ya exclusion ile ya da L3 mutation ile ele alınır.
Aracın bu sınırı dokümante edilmeli.

### Test çalıştırma

Prototipte iki mod da çalışıyor:

```bash
./run.sh                                              # hepsi
./run.sh --test DiscountCalculatorTest#calculatesGoldDiscount   # tek test
```

Tek test modunda coverage %10.2'ye düşüyor — beklenen davranış, doğrulandı.
Bu mod geliştiricinin "benim testim neyi cover ediyor" sorusuna cevap verir.

### Paralel test koşumu — açık risk

Surefire `parallel` açıksa per-test coverage izolasyonu bozulur; JaCoCo agent'ı
global state tutar, `reset()` diğer thread'lerin verisini de siler.
OpenClover bu yüzden paralel koşumu hiç desteklemiyor.

Çözüm seçenekleri: L2'yi ayrı bir sequential profilde koşturmak (nightly),
veya fork-per-class + agent-per-fork. **Bu, tasarımı baştan etkileyen bir
karar — kodlamaya başlamadan netleşmeli.**

## 7. Yol haritası

Her adımın doğrulanabilir bir çıkışı var.

**M1 — CLI çekirdeği (L0 + L1)**
`jacoco.xml` + git diff → overall/new-code coverage, uncovered satırlar,
statik oracle bulguları. Üç metrik modu.
*Doğrulama:* `jacoco-line` modu gerçek bir repoda JaCoCo'nun kendi sayacıyla
birebir tutmalı; `sonar` modu Sonar UI'daki sayıyla ±0.1 tutmalı.

**M2 — Maven plugin + multi-module**
*Doğrulama:* 3+ modüllü gerçek bir iç projede tek komutla aggregate rapor.

**M3 — CI entegrasyonu**
PR'a yorum, quality gate exit code.
*Doğrulama:* Bir PR'da bilerek assertion'sız test eklendiğinde build kırılmalı.

**M4 — L2 redundancy (v2)**
JUnit 5 extension + in-process JaCoCo API.
*Doğrulama:* Gerçek bir repoda `HIGH` confidence bulgularının manuel review'da
**%90+ doğru** çıkması. Bu eşik tutmuyorsa özellik yayınlanmaz.

**M5 — L3 Descartes entegrasyonu ve IDE eklentisi**

## 8. Riskler

| Risk | Etki | Azaltma |
|---|---|---|
| Redundancy false-positive | **Projeyi öldürür** | `HIGH` dışında otomatik silme önerme yok; default "raporla, silme" |
| Paralel test izolasyonu | L2 çalışmaz | M4 öncesi netleştir; gerekirse sequential profil |
| L2 ölçeklenme | Büyük repolarda kullanılamaz | In-process JaCoCo API zorunlu (~60x) |
| Descartes LGPL | Lisans uyumsuzluğu | Ayrı process olarak çağır, link etme |
| Sonar sayı uyuşmazlığı | Güven kaybı | `sonar` metrik modu + net dokümantasyon |

## 9. En önemli tavsiye

**İlk sürüme redundancy koyma.**

L0 + L1 ile çık: Sonar-uyumlu new-code coverage, uncovered satırlar, statik
oracle analizi. Bu tek başına gerçek bir boşluğu dolduruyor, riski düşük ve
kullanıcı toplar.

Redundancy'yi v2'de, gerçek repolarda kalibre ettikten sonra ekle.
En riskli özelliği ilk sürüme koymak bu projenin en olası ölüm şekli:
bir kere yanlış testi sildirirsen kimse aracı bir daha açmaz.
