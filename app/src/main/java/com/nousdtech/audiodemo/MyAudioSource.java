package com.nousdtech.audiodemo;

import android.media.MediaDataSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class MyAudioSource extends MediaDataSource {
    private final byte[] buf;
    private final ByteArrayInputStream is;

    public MyAudioSource(byte[] buf){
        super();
        this.buf=buf;
        is=new ByteArrayInputStream(buf);
    }

    public long getSize() {
        return buf.length;
    }

    public int readAt(long position, byte[] buffer, int offset, int size){
        is.reset();
        is.skip(position);
        return is.read(buffer,offset,size);
    }

    @Override
    public void close() throws IOException {
        if (is != null) {
            try {
                is.close();
            } catch (Exception e) {
                //do nothing
            }
        }
    }
}
