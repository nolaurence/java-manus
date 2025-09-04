package cn.nolaurene.cms.service.sandbox.worker.browser;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.util.concurrent.*;

/**
 * Date: 2025/6/10
 * Author: nolaurence
 * Description:
 */
@Slf4j
@Service
public class FfmpegService {

    @Value("${sandbox.worker.logDir}")
    private String logDir;

    @Value("${sandbox.worker.stream-dir}")
    private String streamDir;

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 1024;
    private static final String DISPLAY = ":1+0,0";

    private static final int FPS = 30;

    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 20, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>()
    );

    private final ConcurrentHashMap<String, Future<?>> streamingTasks = new ConcurrentHashMap<>();
//    private final ConcurrentHashMap<String, FrameRecorder> ffmpegProcesses = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, FFmpegFrameGrabber> grabbers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FFmpegFrameRecorder> recorders = new ConcurrentHashMap<>();

    @Resource
    private BrowserService browserService;

    public void startStreaming(String streamId) throws Exception {

        // 幂等
        if (streamingTasks.containsKey(streamId)) {
            log.warn("[FfmpegService#startStreaming] Streaming task already exists for streamId: {}", streamId);
            return;
        }

        // 启动浏览器
        browserService.startBrowser();

        FFmpegFrameGrabber grabber = FFmpegFrameGrabber.createDefault(DISPLAY);

        // 设置参数
        grabber.setImageWidth(WIDTH);
        grabber.setImageHeight(HEIGHT);
        grabber.setFormat("x11grab");
        grabber.setFrameRate(30);
        grabber.start();

        String outputDir = logDir + "/" + streamDir + "/" + streamId;
        // 确保输出目录存在
        new File(outputDir).mkdirs();

        FFmpegLogCallback.set();
        FFmpegFrameRecorder recorder = FFmpegFrameRecorder.createDefault(outputDir + "/output.m3u8", WIDTH, HEIGHT);
        recorder.setFormat("hls");
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFrameRate(FPS);
        recorder.setGopSize(30);
        recorder.setVideoBitrate(2_000_000); // 码率（2Mbps）
        // HLS 特定参数
        recorder.setOption("hls_time", "1"); // 每个切片时长 1 秒
        recorder.setOption("hls_list_size", "3"); // m3u8 文件中保留的切片数量
        recorder.setOption("hls_flags", "delete_segments"); // 自动删除旧切片
        recorder.setOption("preset", "ultrafast"); // 编码速度优先
        recorder.setOption("tune", "zerolatency"); // 优化低延迟
        recorder.setOption("hls_segment_filename", String.format("%s/%s_%%03d.ts", outputDir, streamId));
        recorder.start();


        Future<?> streamingTask = executor.submit(() -> {
            Frame frame;
            try {
                while ((frame = grabber.grab()) != null) {
                    recorder.record(frame);
                }
            } catch (Exception e) {
                log.warn("[FfmpegService# streaming thread] FFmpegFrameGrabber error: {}", e.getMessage(), e);
            }
        });
        streamingTasks.put(streamId, streamingTask);
        grabbers.put(streamId, grabber);
        recorders.put(streamId, recorder);
    }

    public void stopStreaming(String streamId) {
        Future<?> task = streamingTasks.remove(streamId);
        if (task != null) {
            task.cancel(true);
        } else {
            log.warn("[FfmpegService#stopStreaming] No streaming task found for streamId: {}", streamId);
        }
        try {
            FFmpegFrameGrabber grabber = grabbers.remove(streamId);
            if (grabber != null) {
                grabber.stop();
            } else {
                log.warn("[FfmpegService#stopStreaming] No ffmpeg process found for streamId: {}", streamId);
            }
            FFmpegFrameRecorder recorder = recorders.remove(streamId);
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            } else {
                log.warn("[FfmpegService#stopStreaming] No recorder found for streamId: {}", streamId);
            }
        } catch (Exception e) {
            log.error("[FfmpegService#stopStreaming] Error stopping FFmpegFrameGrabber or recorder: {}", e.getMessage(), e);
        }

    }
}
