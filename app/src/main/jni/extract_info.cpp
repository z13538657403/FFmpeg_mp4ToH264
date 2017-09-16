//
// Created by 张涛 on 17/9/10.
//

#include <stdio.h>
#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>

extern "C"
{
#include "include/libavcodec/avcodec.h"
#include "include/libavformat/avformat.h"
#include "include/libavutil/log.h"
#include "include/libswscale/swscale.h"
#include "include/libavutil/opt.h"
#include "include/libavutil/imgutils.h"
#include "include/libavutil/frame.h"
}

#define LOG(...) __android_log_print(ANDROID_LOG_DEBUG,"Native",__VA_ARGS__)
#define nullptr (void *)0

extern "C"
JNIEXPORT jint JNICALL Java_com_imooc_extractinfotest_MainActivity_extract
        (JNIEnv *env, jobject obj, jstring url)
{
    const char *address = (char*) env->GetStringUTFChars(url , (unsigned char*)nullptr);
    av_register_all();
    avformat_network_init();
    AVFormatContext *ic = avformat_alloc_context();
    int ret = 0;
    ret = avformat_open_input(&ic , address , NULL , NULL);
    if(ret < 0)
    {
        LOG("could not open source %s", address);
        return ret;
    }

    ret = avformat_find_stream_info(ic , NULL);
    if(ret < 0)
    {
        LOG("could not find stream information %s", address);
        return ret;
    }

    //文件名
    LOG("file name: %s", ic->filename);
    LOG("input format: %s", ic->iformat->name);
    //视音频流的个数
    LOG("nb_streams: %d", ic->nb_streams);
    //第一帧的时间戳
    int64_t start_time = ic->start_time / AV_TIME_BASE;
    LOG("start_time: %lld", start_time);
    //码流的总时长
    int64_t duration = ic->duration / AV_TIME_BASE;
    LOG("duration: %lld s", duration);

    int video_stream_idx = av_find_best_stream(ic , AVMEDIA_TYPE_VIDEO , -1 , -1 , NULL , 0);
    if(video_stream_idx >= 0)
    {
        AVStream *video_stream = ic->streams[video_stream_idx];
        LOG("video nb_frames: %lld", video_stream->nb_frames);
        LOG("video codec_id: %d", video_stream->codecpar->codec_id);
        LOG("video codec_name: %s", avcodec_get_name(video_stream->codecpar->codec_id));
        LOG("video width x height: %d x %d", video_stream->codecpar->width, video_stream->codecpar->height);
        LOG("video bitrate %lld kb/s", (int64_t) video_stream->codecpar->bit_rate / 1000);
        LOG("video avg_frame_rate: %d fps", video_stream->avg_frame_rate.num/video_stream->avg_frame_rate.den);
    }

    int audio_stream_idx = av_find_best_stream(ic, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
    if (audio_stream_idx >= 0)
    {
        AVStream *audio_stream = ic->streams[audio_stream_idx];
        LOG("audio codec_id: %d", audio_stream->codecpar->codec_id);
        LOG("audio codec_name: %s", avcodec_get_name(audio_stream->codecpar->codec_id));
        LOG("audio sample_rate: %d", audio_stream->codecpar->sample_rate);
        LOG("audio channels: %d", audio_stream->codecpar->channels);
        LOG("audio frame_size: %d", audio_stream->codecpar->frame_size);
        LOG("audio nb_frames: %lld", audio_stream->nb_frames);
        LOG("audio bitrate %lld kb/s", (int64_t) audio_stream->codecpar->bit_rate / 1000);
    }
    LOG("---------- dumping stream info ----------");
    avformat_close_input(&ic);

    return 1;
}

extern "C"
JNIEXPORT jint JNICALL Java_com_imooc_extractinfotest_MainActivity_mp4ToH264
        (JNIEnv *env, jobject obj, jstring srcPath, jstring desPath)
{
    AVFormatContext *pFormatCtx;
    int i , videoindex;
    AVCodecContext  *pCodecCtx;
    AVCodec *pCodec;
    AVFrame *pFrame;
    uint8_t *out_buffer;
    AVPacket *packet;
    int y_size;
    int ret , got_picture;
    char *filepath = (char*) env->GetStringUTFChars(srcPath , (unsigned char*)nullptr);
    char *outPath = (char*) env->GetStringUTFChars(desPath , (unsigned char*)nullptr);

    FILE *fp_h264 = fopen(outPath , "wb+");

    av_register_all();
    avformat_network_init();
    pFormatCtx = avformat_alloc_context();
    if(avformat_open_input(&pFormatCtx , filepath , NULL , NULL) < 0)
    {
        LOG("Couldn't open input stream.\n");
        return -1;
    }

    if(avformat_find_stream_info(pFormatCtx , NULL) < 0)
    {
        LOG("Couldn't find stream information.\n");
        return -1;
    }

    videoindex = -1;
    for (i = 0 ; i < pFormatCtx->nb_streams ; i++)
    {
        if(pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO)
        {
            videoindex = i;
            break;
        }
    }

    if(videoindex == -1)
    {
        LOG("Didn't find a video stream.\n");
        return -1;
    }

    pCodecCtx = avcodec_alloc_context3(NULL);
    if (pCodecCtx == NULL)
    {
        LOG("Could not allocate AVCodecContext\n");
        return -1;
    }

    int result = avcodec_parameters_to_context(pCodecCtx , pFormatCtx->streams[videoindex]->codecpar);

    pCodec = avcodec_find_decoder(pCodecCtx->codec_id);
    if(pCodec==NULL)
    {
        LOG("Codec not found.\n");
        return -1;
    }

    if(avcodec_open2(pCodecCtx, pCodec,NULL)<0)
    {
        LOG("Could not open codec.\n");
        return -1;
    }

    pFrame = av_frame_alloc();
    packet=(AVPacket *)av_malloc(sizeof(AVPacket));

    av_dump_format(pFormatCtx , 0 , filepath , 0);
    AVBitStreamFilterContext* h264bsfc = av_bitstream_filter_init("h264_mp4toannexb");
    while(av_read_frame(pFormatCtx , packet) >= 0)
    {
        if(packet->stream_index == videoindex)
        {
            av_bitstream_filter_filter(h264bsfc , pCodecCtx , NULL , &packet->data , &packet->size, packet->data , packet->size , 0);
            fwrite(packet->data , 1 , packet->size , fp_h264);
        }
        av_free_packet(packet);
    }

    av_bitstream_filter_close(h264bsfc);
    fclose(fp_h264);

    av_frame_free(&pFrame);
    avcodec_close(pCodecCtx);
    avformat_close_input(&pFormatCtx);

    return 1;
}
