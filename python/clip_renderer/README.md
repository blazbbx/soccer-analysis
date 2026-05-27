# Clip renderer worker

Consumes `clip-render-queue` on RabbitMQ. For each message:

1. Downloads the raw base match video, the overlay WebM, the timeline JSON
   (and the audio WebM if present) from MinIO.
2. Walks the timeline (PLAY / SEEK / PAUSE sync points the coach recorded
   while reviewing the match) and re-renders each segment from the base
   video at the appropriate speed / freeze.
3. Concatenates the segments into an aligned base video.
4. Composites the overlay (and audio) on top, masking magenta as
   transparent.
5. Uploads `rendered.mp4` back to MinIO and publishes the result on
   `clip.rendered`.
