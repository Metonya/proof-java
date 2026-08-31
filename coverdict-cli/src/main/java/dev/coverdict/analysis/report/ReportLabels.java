package dev.coverdict.analysis.report;

import java.util.Map;

/**
 * D-80: one dostu-ad (friendly name) + one-sentence Turkish explanation for
 * every code that can surface in the HTML report - a rule id
 * ({@link dev.coverdict.analysis.model.RuleIds}), an {@code AnalysisReason}
 * code, a {@link dev.coverdict.analysis.model.Severity}/{@link
 * dev.coverdict.analysis.model.Confidence}/{@link
 * dev.coverdict.analysis.model.Classification} enum constant, a PIT mutant
 * status, or a coverage metric mode ({@code jacoco-line}, ...).
 *
 * <p>The machine-readable code is never replaced, only accompanied (hard
 * rule 5: a metric's canonical id is never dropped in favor of its friendly
 * name) - see {@code docs/GLOSSARY.md} for the prose version of the same
 * mapping. {@link #lookup(String)} on an unknown code returns {@code null}
 * so the caller falls back to the raw code rather than inventing text.
 */
public final class ReportLabels {

    public record Label(String name, String description) {
    }

    private static final Map<String, Label> LABELS = Map.ofEntries(
        // Rule ids (docs/rules/*.md) - L0 static-analysis findings.
        entry("NO_RECOGNIZED_ORACLE", "Doğrulamasız test",
            "Test hiçbir tanınan assertion, doğrulama ya da beklenen istisna içermiyor: kod çalışıyor, test yeşil görünüyor, ama hiçbir şey kontrol edilmiyor."),
        entry("TAUTOLOGICAL_ORACLE", "Kendini doğrulayan test",
            "Doğrulamanın sonucu test edilen koddan bağımsız: sabitle sabiti veya bir değeri kendisiyle karşılaştırıyor, her zaman geçer."),
        entry("CATCH_ORACLE_WITHOUT_FAIL", "fail() içermeyen catch bloğu",
            "try/catch içindeki doğrulamalar yalnızca istisna fırlamazsa çalışıyor; kod hata fırlatırsa test yine de geçiyor."),
        entry("NULL_CHECK_ONLY", "Yalnızca null kontrolü",
            "Testteki tüm doğrulamalar sadece null olmadığını kontrol ediyor; değerin içeriği hiç doğrulanmıyor. Zayıf bir test, bozuk değil."),
        entry("PSEUDO_TESTED_METHOD", "Sözde test edilmiş metot",
            "Bir test bu metodu çalıştırıyor ama üretilen tüm mutantlar hayatta kaldı: metodun ne yaptığını hiçbir test fark etmiyor."),
        entry("SUBSUMED_TEST", "Kapsanan test",
            "Bu testin öldürdüğü mutant kümesi, başka bir testin öldürdüğü kümenin alt kümesi; daha geniş test şüpheli, bu değil."),

        // AnalysisReason codes actually constructed in the codebase.
        entry("CHANGED_FILES_EXCLUDED", "Hariç tutulan değişen dosyalar",
            "Değişen bazı dosyalar yapılandırmadaki hariç tutma desenleriyle eşleşti ve analiz dışı bırakıldı."),
        entry("CHANGED_JAVA_OUTSIDE_MODULES", "Modül dışı Java dosyası",
            "Değişen bir Java dosyası hiçbir tanımlı modülün kaynak/test kökünün altına düşmüyor."),
        entry("CHANGED_LINES_ABSENT_FROM_REPORT", "JaCoCo raporunda eksik satırlar",
            "Diff'te değişen bazı satırlar JaCoCo kapsama raporunda hiç görünmüyor."),
        entry("CLASSPATH_ENTRY_UNUSABLE", "Kullanılamayan classpath girdisi",
            "Verilen classpath girdilerinden biri okunamadı ya da geçersiz."),
        entry("CLASSPATH_FILE_UNREADABLE", "Okunamayan classpath dosyası",
            "Classpath dosyası açılamadı ya da okunamadı."),
        entry("CLASSPATH_TOO_LARGE", "Çok büyük classpath",
            "Classpath, işlenebilecek üst sınırı aştığı için kısaltıldı."),
        entry("FINDINGS_TRUNCATED", "Bulgular kısaltıldı",
            "Bulgu sayısı üst sınırı aştığı için liste kısaltıldı; gerçek sayı bundan daha fazla olabilir."),
        entry("MISSING_SOURCE_FILE", "Kaynak dosya bulunamadı",
            "Kapsama raporunda bu dosya için veri var, ama dosyanın kendisi tanımlı kaynak köklerinin hiçbirinde yok."),
        entry("MODULE_WITHOUT_REPORT", "Raporsuz modül",
            "Bu modül için bir JaCoCo raporu verilmedi; modülün kapsaması hesaba katılamadı."),
        entry("MUTATION_CLASSPATH_MISSING", "Mutasyon classpath'i eksik",
            "Mutasyon analizini çalıştırmak için gereken classpath verilmedi."),
        entry("MUTATION_EMPTY_EVIDENCE", "Mutasyon kanıtı boş",
            "Mutasyon çalıştırıldı ama hiçbir mutant üretilmedi ya da toplanmadı."),
        entry("MUTATION_INCONCLUSIVE_STATUS", "Belirsiz mutant durumu",
            "En az bir mutant PIT'in tanıdığı KILLED/SURVIVED/NO_COVERAGE dışında bir durumla döndü."),
        entry("MUTATION_NO_CHANGED_TARGETS", "Mutasyon için hedef yok",
            "Diff'teki değişiklikler mutasyon analizi çalıştırılacak hiçbir üretim metoduna bağlanamadı."),
        entry("MUTATION_TARGET_NOT_BOUND", "Mutasyon hedefi bağlanamadı",
            "Bir üretim metodu mutasyon raporundaki hiçbir sınıfa eşlenemedi."),
        entry("MUTATION_TARGET_UNRESOLVED", "Mutasyon hedefi çözülemedi",
            "Verilen bir --mutation-target bir kaynak dosyaya ya da metoda çözülemedi."),
        entry("MUTATION_TRUNCATED", "Mutasyon verisi kısaltıldı",
            "Mutant sayısı üst sınırı aştığı için liste kısaltıldı."),
        entry("PER_TEST_CLASSPATH_MISSING", "Test bazlı kanıt classpath'i eksik",
            "Test-başına kapsama toplamak için gereken classpath verilmedi."),
        entry("PER_TEST_COLLECTION_FAILED", "Test bazlı kanıt toplanamadı",
            "Test-başına kapsama toplama işlemi hata verdi."),
        entry("PER_TEST_EMPTY_EVIDENCE", "Test bazlı kanıt boş",
            "Test-başına kapsama toplama çalıştı ama hiçbir kayıt üretmedi."),
        entry("PER_TEST_NO_CHANGED_TARGETS", "Test bazlı kanıt için hedef yok",
            "Diff'teki değişiklikler test-başına kapsama toplanacak hiçbir üretim metoduna bağlanamadı."),
        entry("PER_TEST_TARGET_NOT_BOUND", "Test hedefi bağlanamadı",
            "Bir üretim metodu test-başına kapsama verisindeki hiçbir kayda eşlenemedi."),
        entry("PER_TEST_TARGET_UNRESOLVED", "Test hedefi çözülemedi",
            "Verilen bir --per-test-target bir kaynak dosyaya çözülemedi ve atlandı."),
        entry("PER_TEST_TRUNCATED", "Test bazlı kanıt kısaltıldı",
            "Kayıt sayısı üst sınırı aştığı için liste kısaltıldı."),
        entry("REPORT_MISSING_CHANGED_FILE", "Raporda eksik değişen dosya",
            "Değişen bir dosya için JaCoCo raporunda hiç kayıt yok."),
        entry("SUPPRESSED_FINDINGS", "Bastırılmış bulgular",
            "Yapılandırmadaki suppressions ile bazı bulgular gösterimden çıkarıldı."),
        entry("UNPARSEABLE_TEST_SOURCE", "Ayrıştırılamayan test kaynağı",
            "Bir test dosyası AST'ye ayrıştırılamadı; o dosyadaki testler L0 analizine dahil edilemedi."),
        entry("UNTRACKED_JAVA_FILE", "Takip edilmeyen Java dosyası",
            "Git tarafından takip edilmeyen bir Java dosyası diff analizinde yok sayıldı."),
        entry("UNTRACKED_NON_JAVA_FILE", "Takip edilmeyen dosya",
            "Git tarafından takip edilmeyen, Java olmayan bir dosya diff analizinde yok sayıldı."),

        // Severity
        entry("INFO", "Bilgi", "Bir kod sorunu değil; zayıf ya da iyileştirilebilir bir durumu işaret eder."),
        entry("WARNING", "Uyarı", "Testin kanıt değeri sorgulanabilir; incelenmesi önerilir."),

        // Confidence
        entry("HIGH", "Yüksek güven", "Bulgu, örüntünün net bir eşleşmesine dayanıyor."),
        entry("MEDIUM", "Orta güven", "Bulgu muhtemel ama kesin değil; elle doğrulama faydalı olabilir."),
        entry("LOW", "Düşük güven", "Bulgu zayıf bir sinyale dayanıyor; silme önerisi asla bu seviyede olmaz."),
        entry("INCONCLUSIVE", "Belirsiz", "Kanıt bir sonuca varmaya yetmiyor."),

        // Classification (ChangedFile)
        entry("mapped", "Eşleşti", "Değişen dosya bir kaynak koka ve kapsama verisine başarıyla eşlendi."),
        entry("excluded", "Hariç tutuldu", "Değişen dosya yapılandırmadaki bir hariç tutma deseniyle eşleşti."),
        entry("non-executable", "Çalıştırılamaz", "Dosyada kapsanacak çalıştırılabilir satır yok (ör. yalnızca arayüz/sabit tanımı)."),
        entry("unsupported", "Desteklenmiyor", "Dosya türü ya da yapısı bu sürümde analiz edilmiyor."),
        entry("unknown", "Bilinmiyor", "Dosya hiçbir tanımlı kaynak veya test köküyle eşleşmedi."),

        // Mutant statuses (PIT)
        entry("KILLED", "Yakalandı", "En az bir test bu mutasyonu fark etti ve başarısız oldu."),
        entry("SURVIVED", "Kaçırıldı", "Kod bozuldu, hiçbir test fark etmedi; test yine de geçti."),
        entry("NO_COVERAGE", "Hiç çalıştırılmadı", "Bu satırı çalıştıran hiçbir test yok; mutant hiç denenmedi."),
        entry("TIMED_OUT", "Zaman aşımı", "Mutasyonlu kod çalışırken zaman aşımına uğradı (çoğunlukla sonsuz döngü işareti)."),
        entry("NON_VIABLE", "Geçersiz mutant", "Mutasyonlu kod derlenmedi ya da JVM'i başlatamadı; test edilemedi."),
        entry("MEMORY_ERROR", "Bellek hatası", "Mutasyonlu kod çalışırken bellek hatası oluştu."),
        entry("RUN_ERROR", "Çalıştırma hatası", "Mutant çalıştırılırken beklenmeyen bir hata oluştu."),
        entry("STARTED", "Başladı", "Mutant çalıştırılmaya başladı ama sonuç kaydedilmeden süreç sonlandı."),
        entry("NOT_STARTED", "Başlamadı", "Mutant hiç çalıştırılmaya başlanamadı."),

        // Coverage metric modes (hard rule 5: id kept, name is additive)
        entry("jacoco-line", "Satır kapsama (JaCoCo)", "JaCoCo'nun LINE sayacıyla birebir eşleşir: en az bir instruction'ı çalışan satırların oranı."),
        entry("strict-line", "Tam satır kapsama", "Bir satır yalnızca tüm instruction'ları çalıştıysa kapsanmış sayılır; jacoco-line'dan daha katı."),
        entry("sonar-compatible", "Satır + dal kapsama (Sonar)", "SonarQube'un kapsama yüzdesiyle ±0.1 içinde eşleşir: satır ve dal kapsamasının birlikte oranı.")
    );

    private static Map.Entry<String, Label> entry(String code, String name, String description) {
        return Map.entry(code, new Label(name, description));
    }

    public static Label lookup(String code) {
        return code == null ? null : LABELS.get(code);
    }

    public static Map<String, Label> all() {
        return LABELS;
    }

    private ReportLabels() {
    }
}
