package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browser URLs for a match's video stream and field detection image. Both buckets are private: nothing in
 * them can be opened without a signature, so a link that leaks stops working and an access that is revoked
 * ends with the URLs already handed out.
 *
 * <p><b>Video.</b> The match response carries a playlist URL on the API with a short-lived token, issued only
 * to someone who may see the match. The playlist endpoint checks the token (no login header needed, so every
 * player works, including the native iOS one), reads the encoder's playlist from MinIO and replaces each
 * segment name with a presigned GET URL.
 *
 * <p><b>Image.</b> A presigned GET URL, made when the match is sent to the browser.
 *
 * <p>The bucket is never taken from the URL a worker reported: only the key inside the expected bucket.
 */
@Service
@Slf4j
public class MatchMediaUrls {

    private static final String HMAC = "HmacSHA256";
    private static final int MAX_PLAYLIST_BYTES = 5 * 1024 * 1024;
    // The encoder names segments like playlist0.ts; anything else (a path, a URL) is refused.
    private static final Pattern SEGMENT_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,200}");
    private static final Pattern MAP_URI = Pattern.compile("URI=\"([^\"]*)\"");

    private final S3Client s3Client;
    private final S3PresignerService presigner;
    private final Clock clock;
    private final String hlsBucket;
    private final String fieldDetectionBucket;
    private final Duration playlistTokenLifetime;
    private final Duration segmentUrlLifetime;
    private final Duration imageUrlLifetime;
    // Random per start: a restart only invalidates playlist tokens, which the next match fetch renews.
    private final byte[] tokenKey = new byte[32];

    public MatchMediaUrls(S3Client s3Client,
                          S3PresignerService presigner,
                          Clock clock,
                          @Value("${minio.buckets.hls-streams}") String hlsBucket,
                          @Value("${minio.buckets.field-detection}") String fieldDetectionBucket,
                          @Value("${app.media.playlist-token-minutes:60}") long playlistTokenMinutes,
                          @Value("${app.media.segment-url-minutes:240}") long segmentUrlMinutes,
                          @Value("${app.media.image-url-minutes:120}") long imageUrlMinutes) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.clock = clock;
        this.hlsBucket = hlsBucket;
        this.fieldDetectionBucket = fieldDetectionBucket;
        this.playlistTokenLifetime = Duration.ofMinutes(playlistTokenMinutes);
        this.segmentUrlLifetime = Duration.ofMinutes(segmentUrlMinutes);
        this.imageUrlLifetime = Duration.ofMinutes(imageUrlMinutes);
        new SecureRandom().nextBytes(tokenKey);
    }

    /**
     * Playlist URL for the match response, relative to the API base (the frontend puts its API host in front).
     * {@code null} while the match has no stream.
     */
    public String playlistUrl(UUID matchId, String storedManifestUrl) {
        if (matchId == null || storedManifestUrl == null || storedManifestUrl.isBlank()) {
            return null;
        }
        long expires = clock.millis() / 1000 + playlistTokenLifetime.toSeconds();
        return "/api/matches/" + matchId + "/hls/playlist.m3u8?token=" + expires + "." + sign(matchId, expires);
    }

    /** The encoder's playlist with every segment replaced by a presigned URL. */
    public String signedPlaylist(UUID matchId, String token) {
        requireValidToken(matchId, token);
        String playlist = readPlaylist(matchId);
        StringBuilder out = new StringBuilder(playlist.length() * 4);
        for (String rawLine : playlist.split("\n", -1)) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.startsWith("#EXT-X-MAP:")) {
                out.append(signMapUri(matchId, line));
            } else if (line.isBlank() || line.startsWith("#")) {
                out.append(line);
            } else {
                out.append(signSegment(matchId, line.trim()));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Presigned URL of the field detection image, or {@code null} if the stored URL is not in that bucket. */
    public String fieldImageUrl(String storedImageUrl) {
        String key = keyInBucket(storedImageUrl, fieldDetectionBucket);
        if (key == null) {
            return null;
        }
        try {
            return presigner.generateDownloadUrl(fieldDetectionBucket, key, imageUrlLifetime, null, null, null);
        } catch (RuntimeException ex) {
            log.warn("Could not presign field detection image {}", key, ex);
            return null;
        }
    }

    private String signSegment(UUID matchId, String name) {
        if (!SEGMENT_NAME.matcher(name).matches()) {
            throw new ExternalServiceException("The stored playlist has an unexpected segment entry.");
        }
        return presigner.generateDownloadUrl(hlsBucket, matchId + "/" + name, segmentUrlLifetime, null, null, null);
    }

    private String signMapUri(UUID matchId, String line) {
        Matcher matcher = MAP_URI.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        String signed = signSegment(matchId, matcher.group(1));
        return line.substring(0, matcher.start(1)) + signed + line.substring(matcher.end(1));
    }

    private String readPlaylist(UUID matchId) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(hlsBucket)
                    .key(matchId + "/playlist.m3u8")
                    .build());
            if (bytes.asByteArray().length > MAX_PLAYLIST_BYTES) {
                throw new ExternalServiceException("The stored playlist is too large.");
            }
            return bytes.asString(StandardCharsets.UTF_8);
        } catch (NoSuchKeyException ex) {
            throw new NotFoundException("error.match.not_found", new Object[]{matchId}, "No video stream for this match.");
        }
    }

    private void requireValidToken(UUID matchId, String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (matchId == null || dot <= 0) {
            throw invalidToken();
        }
        long expires;
        try {
            expires = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException ex) {
            throw invalidToken();
        }
        byte[] expected = sign(matchId, expires).getBytes(StandardCharsets.US_ASCII);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, given) || clock.millis() / 1000 > expires) {
            throw invalidToken();
        }
    }

    private static ForbiddenException invalidToken() {
        return new ForbiddenException("error.match.stream_link_expired", new Object[0],
                "This video link has expired. Reload the page.");
    }

    private String sign(UUID matchId, long expires) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(tokenKey, HMAC));
            byte[] digest = mac.doFinal((matchId + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    /** The object key of {@code url} when its path starts with {@code /<bucket>/}, otherwise {@code null}. */
    static String keyInBucket(String url, String bucket) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(url.trim()).getPath();
            String prefix = "/" + bucket + "/";
            if (path == null || !path.startsWith(prefix) || path.length() == prefix.length() || path.contains("..")) {
                return null;
            }
            return path.substring(prefix.length());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
