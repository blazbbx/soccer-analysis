package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.TeamRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final S3PresignerService videoStorageService;

    @Transactional
    public Map<String, String> initiateMatchUpload(UploadMatchRequest request) {

        // Ha van csapat ID, betöltjük – ha nincs (null), null marad
        Team homeTeam = request.homeTeamId() != null
                ? teamRepository.findById(request.homeTeamId())
                    .orElseThrow(() -> new RuntimeException("Hazai csapat nem található: " + request.homeTeamId()))
                : null;
        Team awayTeam = request.awayTeamId() != null
                ? teamRepository.findById(request.awayTeamId())
                    .orElseThrow(() -> new RuntimeException("Vendég csapat nem található: " + request.awayTeamId()))
                : null;

        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);

        // Meccs metaadatok (opcionális)
        match.setMatchDate(request.matchDate());


        String extension = "";
        int i = request.originalFilename().lastIndexOf('.');
        if (i > 0) extension = request.originalFilename().substring(i);
        String safeMinioName = match.getId().toString() + extension;

        match.setOriginalFileName(request.originalFilename());
        match.setSavedMinioFileName(safeMinioName);
        match.setOverallStatus("UPLOADING");
        match.setMlStatus("PENDING");
        match.setEncodingStatus("PENDING");

        // 4. Save to DB
        matchRepository.save(match);

        // 5. Generate the Presigned URL using our ultra-safe filename
        String presignedUrl = videoStorageService.generateUploadUrl(safeMinioName);

        return Map.of(
                "uploadUrl", presignedUrl,
                "matchId", match.getId().toString() // Frontend can use this right away!
        );
    }

    @Transactional
    public void markMatchAsProcessing(String savedMinioFileName) {
        Match match = matchRepository.findBySavedMinioFileName(savedMinioFileName)
                .orElseThrow(() -> new RuntimeException("CRITICAL: Match record not found for: " + savedMinioFileName));

        match.setOverallStatus("PROCESSING");
        matchRepository.save(match);
        System.out.println("Match ID " + match.getId() + " status updated to PROCESSING.");
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatchDetails(UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found: " + matchId));
        return toResponse(match);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getAllMatches() {
        return matchRepository.findAll().stream().map(this::toResponse).toList();
    }

    private MatchResponse toResponse(Match match) {
        UUID homeTeamId     = match.getHomeTeam() != null ? match.getHomeTeam().getId()   : null;
        String homeTeamName = match.getHomeTeam() != null ? match.getHomeTeam().getName() : null;
        UUID awayTeamId     = match.getAwayTeam() != null ? match.getAwayTeam().getId()   : null;
        String awayTeamName = match.getAwayTeam() != null ? match.getAwayTeam().getName() : null;

        return new MatchResponse(
                match.getId(),
                homeTeamId,     homeTeamName,
                awayTeamId,     awayTeamName,
                match.getMatchDate(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getOriginalFileName(),
                match.getHlsManifestUrl(),
                match.getTrackingDataUrl(),
                match.getOverallStatus(),
                match.getMlStatus(),
                match.getEncodingStatus(),
                match.getCreatedAt()
        );
    }
}