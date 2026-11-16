package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;

/**
 * Checks a size the browser declared before a presigned upload. The size is then signed into the URL,
 * so MinIO refuses a file of any other length: the limit holds even if the browser lies.
 */
final class UploadSizes {

    static final long DEFAULT_MAX_VIDEO_BYTES = 15L * 1024 * 1024 * 1024;
    static final long DEFAULT_MAX_CLIP_OVERLAY_BYTES = 2L * 1024 * 1024 * 1024;
    static final long DEFAULT_MAX_CLIP_AUDIO_BYTES = 512L * 1024 * 1024;
    static final long DEFAULT_MAX_CLIP_TIMELINE_BYTES = 5L * 1024 * 1024;

    private UploadSizes() {
    }

    static long require(Long size, long max) {
        if (size == null || size <= 0) {
            throw new BadRequestException("validation.upload.size.required", new Object[0],
                    "The file size is required and must be positive.");
        }
        if (size > max) {
            throw new BadRequestException("validation.upload.too_large", new Object[]{size, max},
                    "The file is too large (" + size + " bytes). The limit is " + max + " bytes.");
        }
        return size;
    }
}
