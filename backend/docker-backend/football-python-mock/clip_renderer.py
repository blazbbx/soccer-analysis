import json, logging, os, shutil, subprocess
from pathlib import Path
import boto3, pika
from botocore.client import Config

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s - %(message)s")
logger = logging.getLogger("Clip-Renderer-Dynamic")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "rabbitmq")
MINIO_HOST = os.getenv("MINIO_HOST", "minio")

s3 = boto3.client("s3", endpoint_url=f"http://{MINIO_HOST}:9000",
    aws_access_key_id="SPRING_BOOT_USER", aws_secret_access_key="SuperSecretKey123",
    config=Config(signature_version="s3v4"), region_name="us-east-1")

def run_cmd(args: list[str]) -> None:
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode != 0:
        logger.error("FFmpeg Error: %s", result.stderr)
        raise Exception(f"FFmpeg failed with code {result.returncode}")

def get_video_info(path: str):
    """Lekéri a videó szélességét, magasságát és FPS-ét."""
    cmd = [
        "ffprobe", "-v", "error", "-select_streams", "v:0",
        "-show_entries", "stream=width,height,r_frame_rate",
        "-of", "json", path
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    data = json.loads(res.stdout)
    width = data['streams'][0]['width']
    height = data['streams'][0]['height']
    # FPS kiszámítása (pl "30/1" -> 30)
    fps_raw = data['streams'][0]['r_frame_rate'].split('/')
    fps = float(fps_raw[0]) / float(fps_raw[1])
    return width, height, fps

def build_aligned_base(base_path: str, sync_points: list, work_dir: Path) -> str:
    # Whitespace tisztítás a type mezőkön (pl. "PAUSE  " -> "PAUSE")
    for sp in sync_points:
        sp['type'] = sp['type'].strip()

    sync_points.sort(key=lambda x: x['t'])
    
    # 1. MEGTUDJUK AZ EREDETI MÉRETEKET
    width, height, fps = get_video_info(base_path)
    logger.info(f"Source video detected: {width}x{height} @ {fps} FPS")

    segment_files = []
    for i in range(len(sync_points) - 1):
        curr, nxt = sync_points[i], sync_points[i+1]
        t_dur = round(nxt['t'] - curr['t'], 3)
        m_dur = round(nxt['m'] - curr['m'], 3)
        if t_dur <= 0: continue
        
        seg_path = work_dir / f"seg_{i}.mp4"
        segment_files.append(f"file '{seg_path.absolute()}'")

        if curr['type'] in ['PLAY', 'SEEK']:
            speed = m_dur / t_dur if (m_dur > 0) else 1
            # Eredeti felbontás és FPS megtartása
            run_cmd([
                "ffmpeg", "-y", "-ss", str(curr['m']), "-t", str(max(t_dur, m_dur)), "-i", base_path,
                "-vf", f"setpts={1/speed}*PTS-STARTPTS,fps={fps},scale={width}:{height}",
                "-an", "-c:v", "libx264", "-preset", "ultrafast", "-t", str(t_dur), str(seg_path)
            ])
        else: # PAUSE
            if curr.get('mode') == 'BOARD':
                # BOARD mód: fekete képernyő (a webm overlay és hang fut rá)
                logger.info(f"Segment {i}: BOARD mode pause, generating black screen for {t_dur}s")
                run_cmd([
                    "ffmpeg", "-y",
                    "-f", "lavfi", "-i", f"color=c=black:size={width}x{height}:rate={fps}",
                    "-t", str(t_dur),
                    "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                    str(seg_path)
                ])
            else:
                # Normál PAUSE: képkimerevítés az eredeti felbontásban
                run_cmd([
                    "ffmpeg", "-y", "-ss", str(curr['m']), "-i", base_path,
                    "-vf", f"format=yuv420p,loop=loop={int(t_dur*fps)}:size=1:start=0,scale={width}:{height},fps={fps}",
                    "-an", "-c:v", "libx264", "-preset", "ultrafast", "-t", str(t_dur), str(seg_path)
                ])

    concat_file = work_dir / "concat.txt"
    with open(concat_file, "w") as f: f.write("\n".join(segment_files))
    
    aligned_path = str(work_dir / "base_aligned.mp4")
    run_cmd(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", str(concat_file), "-c", "copy", aligned_path])
    return aligned_path

def render_final(base_aligned: str, overlay: str, audio: str, output: str):
    # A scale2ref továbbra is kell, mert a WebM (rajz) mérete eltérhet a meccsétől!
    # colorkey: magenta (#FF00FF) hatter -> atlatszo, similarity=0.15 enye tores a tomoritesi artifactokra
    filter_complex = (
        "[1:v][0:v]scale2ref=w=iw:h=ih[ovl_scaled][base];"
        "[ovl_scaled]colorkey=color=0xFF00FF:similarity=0.15:blend=0.05[ovl_keyed];"
        "[base][ovl_keyed]overlay=format=auto[v]"
    )
    run_cmd([
        "ffmpeg", "-y", "-i", base_aligned, "-i", overlay, "-i", audio,
        "-filter_complex", filter_complex, "-map", "[v]", "-map", "2:a",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
        "-movflags", "+faststart", output
    ])

# ... (on_message és main marad a régi) ...

def on_message(ch, method, properties, body):
    work_dir = None
    clip_id = None
    try:
        data = json.loads(body)
        clip_id = data.get("clipId")
        match_id = data.get("matchId")
        output_bucket = data.get("outputBucket") or data.get("clipBucket")
        if not clip_id or not match_id:
            raise ValueError("clipId and matchId are required in clip render message")
        if not output_bucket:
            raise ValueError("outputBucket is required in clip render message")

        ch.basic_publish(
            exchange="video-exchange",
            routing_key="clip.rendered",
            body=json.dumps({
                "clipId": clip_id,
                "status": "PROCESSING",
            }),
            properties=pika.BasicProperties(content_type="application/json"),
        )

        work_dir = Path(f"./work_{clip_id}")
        work_dir.mkdir(exist_ok=True)
        f_base, f_ovl, f_aud, f_json, f_final = [str(work_dir / x) for x in ["base.mp4", "overlay.webm", "audio.mp3", "sync.json", "final.mp4"]]
        clip_prefix = f"{match_id}/{clip_id}/"
        
        s3.download_file(data["baseBucket"], data["baseObjectKey"], f_base)
        s3.download_file(output_bucket, clip_prefix + "overlay.webm", f_ovl)
        s3.download_file(output_bucket, clip_prefix + "audio.mp3", f_aud)
        s3.download_file(output_bucket, clip_prefix + "timeline.json", f_json)
        
        with open(f_json, "r") as f: sync_points = json.load(f)
        base_aligned = build_aligned_base(f_base, sync_points, work_dir)
        render_final(base_aligned, f_ovl, f_aud, f_final)
        
        with open(f_final, "rb") as f:
            s3.put_object(Bucket=output_bucket, Key=clip_prefix + "rendered.mp4", Body=f, ContentType="video/mp4")

        ch.basic_publish(
            exchange="video-exchange",
            routing_key="clip.rendered",
            body=json.dumps({
                "clipId": clip_id,
                "status": "COMPLETED",
            }),
            properties=pika.BasicProperties(content_type="application/json"),
        )
        logger.info("SUCCESS: Clip %s rendered.", clip_id)
    except Exception as e:
        logger.exception("Job failed!")
        ch.basic_publish(
            exchange="video-exchange",
            routing_key="clip.rendered",
            body=json.dumps({
                "clipId": clip_id,
                "status": "ERROR",
                "errorMessage": str(e),
            }),
            properties=pika.BasicProperties(content_type="application/json"),
        )
    finally:
        if work_dir and work_dir.exists(): shutil.rmtree(work_dir)
        ch.basic_ack(delivery_tag=method.delivery_tag)

def main():
    conn = pika.BlockingConnection(pika.ConnectionParameters(RABBITMQ_HOST, 5672, "/", pika.PlainCredentials("admin", "password")))
    ch = conn.channel()
    ch.queue_declare(queue="clip-render-queue", durable=True)
    ch.basic_qos(prefetch_count=1)
    ch.basic_consume(queue="clip-render-queue", on_message_callback=on_message)
    logger.info("Worker started...")
    ch.start_consuming()

if __name__ == "__main__": main()