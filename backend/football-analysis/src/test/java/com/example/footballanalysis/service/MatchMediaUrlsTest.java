package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchMediaUrlsTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Mock private S3Client s3Client;
    @Mock private S3PresignerService presigner;

    private final UUID matchId = UUID.randomUUID();
    private MatchMediaUrls urls;

    @BeforeEach
    void setUp() {
        urls = mediaUrls(Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(presigner.generateDownloadUrl(eq("hls-streams"), any(), any(Duration.class), isNull(), isNull(), isNull()))
                .thenAnswer(invocation -> "https://minio/" + invocation.getArgument(1) + "?sig");
    }

    @Test
    void playlistUrl_isAnApiPathWithAToken_andNullWithoutAStream() {
        String url = urls.playlistUrl(matchId, "http://minio:9000/hls-streams/x/playlist.m3u8");

        assertThat(url).startsWith("/api/matches/" + matchId + "/hls/playlist.m3u8?token=");
        assertThat(urls.playlistUrl(matchId, null)).isNull();
    }

    @Test
    void signedPlaylist_replacesEverySegmentAndKeepsTheTags() {
        storedPlaylist("#EXTM3U\r\n#EXT-X-TARGETDURATION:5\r\n#EXTINF:5.0,\r\nplaylist0.ts\r\n#EXTINF:5.0,\r\nplaylist1.ts\r\n#EXT-X-ENDLIST\r\n");

        String body = urls.signedPlaylist(matchId, token(urls.playlistUrl(matchId, "stored")));

        assertThat(body).contains("#EXTM3U\n", "#EXT-X-TARGETDURATION:5\n", "#EXT-X-ENDLIST\n")
                .contains("https://minio/" + matchId + "/playlist0.ts?sig\n")
                .contains("https://minio/" + matchId + "/playlist1.ts?sig\n")
                .doesNotContain("\r");
    }

    @Test
    void signedPlaylist_signsTheInitSegmentOfAnFmp4Stream() {
        storedPlaylist("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:5.0,\nseg0.m4s\n");

        String body = urls.signedPlaylist(matchId, token(urls.playlistUrl(matchId, "stored")));

        assertThat(body).contains("#EXT-X-MAP:URI=\"https://minio/" + matchId + "/init.mp4?sig\"");
    }

    @Test
    void signedPlaylist_refusesASegmentThatPointsElsewhere() {
        storedPlaylist("#EXTM3U\n#EXTINF:5.0,\n../medical-docs/x.jpg\n");

        assertThatThrownBy(() -> urls.signedPlaylist(matchId, token(urls.playlistUrl(matchId, "stored"))))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void signedPlaylist_refusesAnExpiredToken() {
        String token = token(urls.playlistUrl(matchId, "stored"));
        MatchMediaUrls later = mediaUrls(Clock.fixed(NOW.plus(Duration.ofMinutes(61)), ZoneOffset.UTC));

        // A token from another instance's key would also fail, so check expiry on the same key:
        assertThatThrownBy(() -> urls.signedPlaylist(matchId, expiredCopyOf(token)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> later.signedPlaylist(matchId, token))
                .isInstanceOf(ForbiddenException.class);
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void signedPlaylist_refusesATamperedOrForeignToken() {
        String token = token(urls.playlistUrl(matchId, "stored"));

        assertThatThrownBy(() -> urls.signedPlaylist(UUID.randomUUID(), token))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> urls.signedPlaylist(matchId, token + "x"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> urls.signedPlaylist(matchId, "garbage"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> urls.signedPlaylist(matchId, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void fieldImageUrl_signsOnlyInsideTheFieldDetectionBucket() {
        when(presigner.generateDownloadUrl(eq("field-detection"), eq(matchId + ".jpg"), any(Duration.class), isNull(), isNull(), isNull()))
                .thenReturn("https://minio/field-detection/signed");

        assertThat(urls.fieldImageUrl("http://localhost:9000/field-detection/" + matchId + ".jpg"))
                .isEqualTo("https://minio/field-detection/signed");
        assertThat(urls.fieldImageUrl("http://localhost:9000/medical-docs/secret.jpg")).isNull();
        assertThat(urls.fieldImageUrl(null)).isNull();
    }

    private MatchMediaUrls mediaUrls(Clock clock) {
        return new MatchMediaUrls(s3Client, presigner, clock, "hls-streams", "field-detection", 60, 240, 120);
    }

    private void storedPlaylist(String content) {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content.getBytes(StandardCharsets.UTF_8)));
    }

    private static String token(String playlistUrl) {
        return playlistUrl.substring(playlistUrl.indexOf("token=") + "token=".length());
    }

    // Same signature, an earlier expiry: the signature no longer matches, so it is refused.
    private static String expiredCopyOf(String token) {
        return "1" + token.substring(token.indexOf('.'));
    }
}
