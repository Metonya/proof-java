# demo-bank — test quality analyzer prototype

Çalışan bir prototip. Fikri ispatlamak için var, ürün değil.
Geliştirme planı için `PLAN.md`.

## Ne yapıyor

Kasıtlı olarak kötü testler içeren multi-module bir Java repo üzerinde:

- overall coverage (üç farklı metrik tanımıyla)
- new-code coverage (git diff kesişimi)
- uncovered satırlar, aralık olarak
- işlevsiz test tespiti (assertion yok / tautolojik / catch içi oracle)
- redundant test tespiti (duplicate cluster + eager test)

## Çalıştırma

Gereksinimler: JDK 17+, Python 3.8+, JaCoCo dağıtımı.

```bash
export JACOCO_HOME=/path/to/jacoco       # jacocoagent.jar + jacococli.jar içeren dizin
./run.sh                                  # hepsi
./run.sh --test DiscountCalculatorTest#calculatesGoldDiscount    # tek test
./run.sh --base main --metric sonar       # farklı referans / metrik
```

Çıktılar:

- `build/tqa.html` — bulgular
- `build/tqa.json` — makine okunabilir, CI için
- `build/jacoco-html/index.html` — standart JaCoCo raporu

## Repo yapısı

```
core/     DiscountCalculator, IbanValidator, AccountDto
api/      FeeService
harness/  mini test runner — per-test JaCoCo exec dump alır
../tqa/   analiz motoru (tqa.py)
```

## Ekili test kokuları

| Test | Koku |
|---|---|
| `DiscountCalculatorTest#testCalculateForGoldCustomer` | `calculatesGoldDiscount` ile birebir duplicate |
| `DiscountCalculatorTest#testCalculateRuns` | assertion yok |
| `DiscountCalculatorTest#testCalculateReturnsSomething` | tautolojik assertion |
| `DiscountCalculatorTest#shouldNotThrowForStaffTier` | oracle catch bloğunda, normalde hiç çalışmaz |
| `IbanValidatorTest#testMaskAndValidate` | eager test — iki odaklı testin birleşimi |
| `AccountDtoTest#testGettersAndSetters` | DTO coverage şişirmesi (araç yakalayamaz, bkz. PLAN.md) |
| `FeeServiceTest#testFeeCategoryLow` | assertion yok |

## Prototipin sınırları

- **Test harness JUnit değil.** Sandbox Maven Central'a erişemediği için
  `harness/` altında minimal bir runner var. Per-test coverage mekanizması
  (`RT.getAgent().reset()` / `getExecutionData(true)`) gerçek üründekiyle
  birebir aynı; üründe bunu bir JUnit 5 `TestExecutionListener` yapar.
- **Statik analiz regex tabanlı.** Üründe JavaParser AST olmalı.
- **Per-test analiz her exec için ayrı JVM açıyor** — test başına ~309ms.
  Üründe in-process JaCoCo API kullanılmalı (~60x hızlanma). Ölçüm ve gerekçe
  `PLAN.md` bölüm 4'te.
- **Mutation testing (L3) yok.** Descartes entegrasyonu planlandı, yapılmadı.
