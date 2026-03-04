package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final S3PresignerService videoStorageService; // Assuming you have this

    @Transactional
    public Map<String, String> initiateMatchUpload(UploadMatchRequest request) {

        // 1. Create the Match object FIRST so we can explicitly generate its ID
        Match match = new Match();
        match.setId(UUID.randomUUID()); // We assign the UUID manually here
        match.setHomeTeamId(request.homeTeamId());
        match.setAwayTeamId(request.awayTeamId());
        // match.setLocation(request.location()); // Assuming you add this field

        // 2. Safely extract the extension (e.g., ".mp4")
        String extension = "";
        int i = request.originalFilename().lastIndexOf('.');
        if (i > 0) {
            extension = request.originalFilename().substring(i);
        }

        // 3. The MinIO filename is strictly "UUID.mp4" - 100% safe and predictable!
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
                .orElseThrow(() -> new RuntimeException("Match not found for ID: " + matchId));

        // Transform the DB Entity into a clean JSON Response
        return new MatchResponse(
                match.getId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getOriginalFileName(),
                match.getOverallStatus(),
                match.getMlStatus(),
                match.getEncodingStatus(),
                match.getHlsManifestUrl(),
                match.getTrackingDataUrl(),
                match.getCreatedAt()
        );
    }
}