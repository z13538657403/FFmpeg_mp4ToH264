package com.imooc.extractinfotest;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class MainActivity extends Activity
{
    static
    {
        System.loadLibrary("avutil-55");
        System.loadLibrary("swresample-2");
        System.loadLibrary("avcodec-57");
        System.loadLibrary("avformat-57");
        System.loadLibrary("swscale-4");
        System.loadLibrary("avfilter-6");
        System.loadLibrary("nativextract");
    }

    public native int extract(String url);
    public native int mp4ToH264(String srcPath , String desPath);

    private SurfaceView mSurfaceView;
    private TextView startTv;
    private static MediaCodec mCodec;
    private static final int BUFFER_SIZE = 1024 * 2;
    private byte[] mBuffer;
    private final static String MIME_TYPE = "video/avc";
    private final static int VIDEO_WIDTH = 1920;
    private final static int VIDEO_HEIGHT = 768;
    private final static int TIME_INTERNAL = 3000;
    private int mCount = 0;
    private final static int HEAD_OFFSET = 512;
    Thread readFileThread;
    private InputStream is = null;
    private FileInputStream fs = null;
    private File h264File;

    static final int  framebufsize = 1024*400;//200000;//
    static final int  readbufsize = 1024*200;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        startTv = (TextView) findViewById(R.id.start_tv);
        mBuffer = new byte[BUFFER_SIZE];

//        "http://192.168.0.104/sintel.mp4"
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/sintel.mp4";
//        int ret = extract(path);
//        Log.d("MainActivity111" , "the ret = " + ret);
        int result = mp4ToH264(path , Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.h264");

        mSurfaceView.post(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,VIDEO_WIDTH, VIDEO_HEIGHT);
                mCodec.configure(mediaFormat, mSurfaceView.getHolder().getSurface(),null, 0);
                mCodec.start();
            }
        });

        startTv.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                h264File = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.h264");
                readFileThread = new Thread(readFile);
                readFileThread.start();
            }
        });
    }

    Runnable readFile = new Runnable()
    {
        @Override
        public void run()
        {
            int jjj=0;
            int h264Read = 0;
            int frameOffset = 0;
            byte[] buffer = new byte[readbufsize];
            byte[] framebuffer = new byte[framebufsize];
            boolean readFlag = true;
            try
            {
                fs = new FileInputStream(h264File);
                is = new BufferedInputStream(fs);
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            while (!Thread.interrupted() && readFlag)
            {
                try
                {
                    int length = is.available();
                    Log.d("MainActivity" , "-------------" + length);
                    if (length > 0)
                    {
                        int count = is.read(buffer);
                        Log.d("MainActivity" , buffer[0] + "-----" + buffer[1] + "------" + buffer[2] + "------" + buffer[3]);

                        h264Read += count;
                        if (frameOffset + count < framebufsize)
                        {
                            System.arraycopy(buffer, 0, framebuffer, frameOffset, count);
                            frameOffset += count;
                        }
                        else
                        {
                            frameOffset = 0;
                            System.arraycopy(buffer, 0, framebuffer, frameOffset, count);
                            frameOffset += count;
                        }

                        // Find H264 head
                        int offset = findHead(framebuffer, frameOffset);
                        while (offset > 0)
                        {
                            if (checkHead(framebuffer, 0))
                            {
                                // Fill decoder
                                boolean flag = onFrame(framebuffer, 0, offset);
                                if (flag)
                                {
                                    byte[] temp = framebuffer;
                                    framebuffer = new byte[framebufsize];
                                    System.arraycopy(temp, offset, framebuffer, 0, frameOffset - offset);
                                    frameOffset -= offset;
                                    offset = findHead(framebuffer, frameOffset);
                                }
                                else
                                {
                                    jjj= jjj+1;
                                    if(jjj==1000)
                                        jjj=0;

                                }
                            }
                            else
                            {

                                offset = 0;
                            }
                        }
                    }
                    else
                    {
                        h264Read = 0;
                        frameOffset = 0;
                        readFlag = false;
                        // Start a new thread
                        readFileThread = new Thread(readFile);
                        readFileThread.start();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }


                try
                {
                    Thread.sleep(10);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    };

    static int findHead(byte[] buffer, int len)
    {
        int i;
        for (i = HEAD_OFFSET; i < len; i++)
        {
            if (checkHead(buffer, i))
                break;
        }
        if (i == len)
            return 0;
        if (i == HEAD_OFFSET)
            return 0;
        return i;
    }

    static boolean checkHead(byte[] buffer, int offset)
    {
        if (buffer[offset] == 0 && buffer[offset + 1] == 0 && buffer[offset + 2] == 0 && buffer[3] == 1)
            return true;
        if (buffer[offset] == 0 && buffer[offset + 1] == 0 && buffer[offset + 2] == 1)
            return true;
        return false;
    }

    public boolean onFrame(byte[] buf, int offset, int length)
    {
        ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
        int inputBufferIndex = mCodec.dequeueInputBuffer(100);

        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mCodec.queueInputBuffer(inputBufferIndex, 0, length, mCount	* TIME_INTERNAL, 0);
            mCount++;
        } else {
            return false;
        }

        // Get output buffer index
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);
        while (outputBufferIndex >= 0) {
            mCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
        return true;
    }
}
