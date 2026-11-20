package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Club;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a club may collect sport medical data (docs/sportorvosi-jogszeruseg.md, Bekapcsolási feltételek): the
 * operator recorded the club's signed data processing agreement of the current version (that makes collection
 * available to the club), and the club named the law and has a privacy notice. The system cannot judge the club's
 * decision, only that its traces are there.
 * <p>
 * The agreement's version and link come from configuration and are empty until the approved text exists. While the
 * version is empty no signed copy can be recorded and no club is ready, so collection cannot be turned on against a
 * placeholder.
 */
@Component
public class MedicalCompliance {

    public static final String MISSING_AGREEMENT = "agreement";
    public static final String MISSING_LEGAL_BASIS = "legalBasis";
    public static final String MISSING_NOTICE = "notice";

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

    /** The agreement version a club's signed copy must be of, or null while the approved text does not exist. */
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

    /** What the club still lacks, in a fixed order: agreement, legalBasis, notice. */
    public List<String> missing(Club club) {
        List<String> missing = new ArrayList<>();
        if (!hasAgreement(club)) {
            missing.add(MISSING_AGREEMENT);
        }
        if (club == null || club.getMedicalLegalBasis() == null || club.getMedicalLegalBasis().isBlank()) {
            missing.add(MISSING_LEGAL_BASIS);
        }
        if (club == null || !hasNotice(club)) {
            missing.add(MISSING_NOTICE);
        }
        return missing;
    }

    /**
     * The operator recorded the club's signed agreement of the current version. A new agreement version needs a new
     * signed copy.
     */
    public boolean hasAgreement(Club club) {
        return club != null && dpaVersion != null && dpaVersion.equals(club.getMedicalAgreementVersion());
    }

    public static boolean hasNotice(Club club) {
        return club.getMedicalNoticeUrl() != null || club.getMedicalNoticeObjectKey() != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
