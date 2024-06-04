package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileInputStream;


public class TftpListener implements Runnable {

    private TftpConnectionHandler handler;
    private int expectedSendBlockNumber;
    private int expectedReceiveBlockNumber;
    private List<Byte> dataRecieved;
    private TftpKeyboard keyboard;

    public TftpListener(TftpConnectionHandler handler, TftpKeyboard keyboardThread) {
        this.handler = handler;
        this.expectedSendBlockNumber = 1;
        this.expectedReceiveBlockNumber = 1;
        this.dataRecieved = new ArrayList<>();
        this.keyboard = keyboardThread;
    }

    public void run() {
        int read;
        try{
            while (!handler.shouldTerminate() && (read = handler.in.read()) >= 0) {
                byte[] msg = handler.encdec.decodeNextByte((byte) read);
                if (msg != null) {
                    short opCode = (short) (((short) msg[0]) << 8 | (short) (msg[1]));
                    process(msg, opCode);
                } 
            }
        } catch (IOException e) {}
    }

    private void process(byte[] msg, short opCode) {
        switch (opCode){// notify keyboard on wrq ack and errors
            case 3:
            short packetSize = (short) (( msg[2]) << 8 |  (msg[3] & 0xFF));
            short blockNumber = (short) (( msg[4]) << 8 |  (msg[5] & 0xFF));
            if (blockNumber == expectedReceiveBlockNumber){
                if (packetSize == 512) {
                    byte[] data = Arrays.copyOfRange(msg, 6, 6 + packetSize);
                    for (byte b : data) {
                        dataRecieved.add(b);
                    }
                    expectedReceiveBlockNumber++;
                    short ackBlockNumber = blockNumber;
                    sendAck(ackBlockNumber);
                }
                else {
                    byte[] data = Arrays.copyOfRange(msg, 6, 6 + packetSize);
                    for (byte b : data) {
                        dataRecieved.add(b);
                    }
                    byte[] fileBytes = new byte[dataRecieved.size()];
                    for (int i = 0; i < dataRecieved.size(); i++) {
                        fileBytes[i] = dataRecieved.get(i);
                    }
                    if(handler.isDownloading){
                        try {
                            Files.write(Paths.get(System.getProperty("user.dir"), handler.fileName), fileBytes);
                            short ackBlockNumber = blockNumber;
                            sendAck(ackBlockNumber);
                        } catch (IOException e) {}
                    }
                    else {
                        printDirq(fileBytes);
                    }
                    handler.isDownloading = false;
                    handler.fileName = "";
                    expectedReceiveBlockNumber = 1;
                    dataRecieved.clear();
                    synchronized(keyboard){
                        keyboard.notify();
                    }
                }
            }  
            break;

            case 4:
                short ackBlockNumber = (short) (( msg[2]) << 8 |  (msg[3] & 0xFF));
                handler.isAck = true;
                System.out.println("ACK " + ackBlockNumber);
                synchronized(keyboard){
                    keyboard.notify();
                }
            break;

            case 5:
            short errorCode = (short) (( msg[2]) << 8 |  (msg[3] & 0xFF));
            byte[] errorMsg = Arrays.copyOfRange(msg, 4, msg.length);
            String error = new String(errorMsg);
            if (errorCode == 1){
                System.out.println("Error " + errorCode + " " + error);
                Path filePath = Paths.get(System.getProperty("user.dir"), handler.fileName);
                if(Files.exists(filePath)){
                    try {
                        Files.delete(filePath);
                    } catch (IOException e) {}
                }
            }
            else if(errorCode == 5 || errorCode == 6 || errorCode == 7 || errorCode == 0) {
                handler.isAck = false;
                System.out.println("Error " + errorCode + " " + error);
            }
            handler.isDownloading = false;
            handler.fileName = "";
            expectedReceiveBlockNumber = 1;
            dataRecieved.clear();
            synchronized(keyboard){
                keyboard.notify();
            }
            break;


            case 9:
            byte isAdded = msg[2];
            byte[] fileName = Arrays.copyOfRange(msg, 3, msg.length);
            String file = new String(fileName);
            String action = "";
            if (isAdded == 1) 
                action = "add";
            else 
                action = "del";
            System.out.println("BCAST " + action + " " + file);
            break;
    }   
}     
    

    private void sendAck(short blockNumber) {
        byte[] ack = new byte[4];
        short opCode = 4;
        ack[0] = (byte) (opCode >> 8);
        ack[1] = (byte) (opCode & 0xFF);
        ack[2] = (byte) (blockNumber >> 8);
        ack[3] = (byte) (blockNumber & 0xFF);
        handler.send(ack);
    }

    private void printDirq(byte[] dirqMsg) {
        int i = 0;
        while (i < dirqMsg.length) {
            int j = i;
            while (j < dirqMsg.length && dirqMsg[j] != 0) {
                j++;
            }
            byte[] fileName = Arrays.copyOfRange(dirqMsg, i, j);
            System.out.println(new String(fileName));
            i = j + 1;
        }
    }
}
