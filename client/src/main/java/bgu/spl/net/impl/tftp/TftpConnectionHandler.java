package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

public class TftpConnectionHandler{

    protected BufferedInputStream in;
    protected BufferedOutputStream out;
    private volatile boolean shouldTerminate;
    protected TftpEncoderDecoder encdec;
    protected boolean isUploading;
    protected boolean isDownloading;
    protected String fileName;
    protected boolean isAck;
    protected int expectedSendBlockNumber;

    public TftpConnectionHandler(BufferedInputStream in, BufferedOutputStream out) {
        this.in = in;
        this.out = out;
        this.shouldTerminate = false;
        this.encdec = new TftpEncoderDecoder();
        this.isUploading = false;
        this.isDownloading = false;
        this.fileName = "";
        this.isAck = false;
    }

    protected void send(byte[] msg) {
         try{
            if (msg != null) {
                out.write(encdec.encode(msg));
                out.flush();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void terminate() {
        shouldTerminate = true;
        try {
            in.close();
        } catch (Exception e) {}

    }

    public boolean shouldTerminate() {
        return shouldTerminate;
    }
    
    public boolean receiveAck() {
        return isAck;
    }
}
