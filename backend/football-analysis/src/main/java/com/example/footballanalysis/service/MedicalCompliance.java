package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Club;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a club may collect sport medical data (docs/sportorvosi-jogszeruseg.md, Bekapcsolási feltételek): it named
 * the law, has a privacy notice, and accepted the current data processing agreement. The system cannot judge the
 * club's decision, only that its traces are there.
 * <p>
 * The agreement's version and link come from configuration and are empty until the approved text exists. While the
 * version is empty no club is ready, so collection cannot be turned on against a placeholder.
 */
@Component
public class MedicalCompliance {

    public static final String MISSING_LEGAL_BASIS = "legalBasis";
    public static final String MISSING_NOTICE = "notice";
    public static final String MISSING_DPA = "dpa";

    private final String dpaVersion;
    private final String dpaDocumentUrl;
    private final String noticeTemplateUrl;

    public MedicalCompliance(@Value("${app.medical.dpa-version:}") String dpaVersion,
                             @Value("${app.medical.dpa-document-url:}") String dpaDocumentUrl,
                             @Value("${app.medical.notice-template-url:}") String noticeTemplateUrl) {
        this.dpaVersion = blankToNull(dpaVersion);
        this.dpaDocumentUrl = blankToNull(dpaDocumentUrl);
        this.noticeTemplateUrl = blankToNull(noticeTemplateUrl);
    }

    /** The agreement version clubs must accept now, or null while the approved text does not exist. */
    public String currentDpaVersion() {
        return dpaVersion;
    }

    /** Null together with {@link #currentDpaVersion()}: there is nothing to link to before the text exists. */
    public String dpaDocumentUrl() {
        return dpaVersion == null ? null : dpaDocumentUrl;
    }

    public String noticeTemplateUrl() {
        return noticeTemplateUrl;
    }

    public boolean isClubReady(Club club) {
        return missing(club).isEmpty();
    }

    /** What the club still lacks, in a fixed order: legalBasis, notice, dpa. */
    public List<String> missing(Club club) {
        List<String> missing = new ArrayList<>();
        if (club == null || club.getMedicalLegalBasis() == null || club.getMedicalLegalBasis().isBlank()) {
            missing.add(MISSING_LEGAL_BASIS);
        }
        if (club == null || !hasNotice(club)) {
            missing.add(MISSING_NOTICE);
        }
        if (club == null || dpaVersion == null || !dpaVersion.equals(club.getMedicalDpaVersion())) {
            missing.add(MISSING_DPA);
        }
        return missing;
    }

    public static boolean hasNotice(Club club) {
        return club.getMedicalNoticeUrl() != null || club.getMedicalNoticeObjectKey() != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
