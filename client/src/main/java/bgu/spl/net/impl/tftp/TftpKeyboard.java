package bgu.spl.net.impl.tftp;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.FileInputStream;

public class TftpKeyboard implements Runnable{

    private TftpConnectionHandler handler;
    private ConcurrentLinkedQueue<byte[]> dataQueue;
    Path filePath;
    private boolean loggedIn;

    public TftpKeyboard(TftpConnectionHandler handler) {
        this.handler = handler;
        this.dataQueue = new ConcurrentLinkedQueue<>();
        this.loggedIn = false;
    }

    public void run() {
        BufferedReader keyboardInput = new BufferedReader(new InputStreamReader(System.in));
        String message;
        try {
        while (!handler.shouldTerminate() && (message = keyboardInput.readLine()) != null){
            try {
                if (message != null) {
                    process(message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void process(String line) {
        String command = line.split(" ")[0];
        String content = "";
        if (line.split(" ").length > 1) {
            content = line.substring(command.length() + 1);
        }
        short opCode = 0;
        if (!isValidCommand(command, content)) {
            System.out.println("Invalid command");
            return;
        }
        switch (command) {
            case "LOGRQ":
                opCode = 7;
                synchronized(this) {
                    handler.send(createMsg(opCode, content));
                    try{
                        this.wait();
                    } catch (Exception e) {}
                }
                if (handler.isAck)
                    loggedIn = true;
                break;
            
            case "DELRQ":
                opCode = 8;
                synchronized(this) {               
                    handler.send(createMsg(opCode, content));
                    try{
                        this.wait();
                    } catch (Exception e) {}
                }
                break;

            case "RRQ":
                opCode = 1;

                handler.fileName = content;
                filePath = Paths.get(System.getProperty("user.dir"), content);
                if(Files.exists(filePath)){
                    System.out.println("File already exists");
                    return;
                }
                else{
                    filePath = Paths.get(System.getProperty("user.dir"));
                    try {
                        Files.createFile(filePath.resolve(content));
                    } catch (Exception e) {}
                }
                handler.isDownloading = true;
                synchronized(this) {                
                    handler.send(createMsg(opCode, content));
                    try {
                        this.wait();
                    } catch (Exception e) {} 
                }
                break;

            case "WRQ":
                opCode = 2;
                handler.fileName = content;
                filePath = Paths.get(System.getProperty("user.dir"), content);
                if(!Files.exists(filePath)){
                    System.out.println("File does not exist");
                    return;
                }
                else
                    synchronized(this) {
                        handler.send(createMsg(opCode, content));
                        handler.expectedSendBlockNumber = 0;
                        try{
                            this.wait();
                        } catch (Exception e) {}
                    }
                    if (handler.receiveAck()){
                        try {
                            FileInputStream fileInputStream = new FileInputStream(filePath.toString());
                            //byte[] fileBytes = fileInputStream.readAllBytes(); 
    
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                    
                            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                    
                            byte[] fileBytes = outputStream.toByteArray();
    
                            fileInputStream.close();
                            dataSalami(fileBytes);
                        }  catch (Exception e) {}
                        while(handler.isAck && !dataQueue.isEmpty()){
                            handler.expectedSendBlockNumber++;
                            synchronized(this) {
                                handler.send(dataQueue.poll());
                                try{
                                    this.wait();
                                } catch (Exception e) {}
                            }
                        }    
                         
                    }
                break;

            case "DIRQ":
                opCode = 6;
                byte[] dirqmsg = new byte[2];
                dirqmsg[0] = (byte) (opCode >> 8);
                dirqmsg[1] = (byte) (opCode & 0xFF);
                synchronized(this){
                    handler.send(dirqmsg);
                    try {
                        this.wait();
                    } catch (Exception e) {}
                }
                break;

            case "DISC":
                opCode = 10;
                byte[] dcmsg = new byte[2];
                dcmsg[0] = (byte) (opCode >> 8);
                dcmsg[1] = (byte) (opCode & 0xFF);
                if (loggedIn){
                    synchronized(this){
                        handler.send(dcmsg);
                        try {
                            this.wait();
                        } catch (Exception e) {}
                    }
                }
                handler.terminate();
                break;               
        }
    }

    private boolean isValidCommand(String command, String content) {
        if (command.equals("LOGRQ") || command.equals("DELRQ") || command.equals("RRQ") || command.equals("WRQ")) {
            if (content.length() == 0) {
                return false;
            }
            return true;
        }
        else if (command.equals("DIRQ") || command.equals("DISC")) {
            if (content.length() != 0) {
                return false;
            }
            return true;
        }
        return false;
    }
    private byte[] createMsg(short opCode, String content) {
        byte[] contentBytes = content.getBytes();
        byte[] msg = new byte[contentBytes.length + 3];
        msg[0] = (byte) (opCode >> 8);
        msg[1] = (byte) (opCode & 0xFF);
        System.arraycopy(contentBytes, 0, msg, 2, contentBytes.length);
        msg[msg.length - 1] = (byte) 0;
        return msg;
    }

    private void dataSalami(byte[] data) {
        short opCode = 3;
        short packetSize = 0;
        short blockNumber = 1;
        while (data.length - (blockNumber - 1) * 512 >= 0) {
            packetSize = (short) Math.min(512, data.length - (blockNumber - 1) * 512);
            byte[] dataPacket = new byte[6 + packetSize];
            dataPacket[0] = (byte)((opCode >> 8));
            dataPacket[1] = (byte)(opCode & 0xFF);
            dataPacket[2] = (byte)((packetSize >> 8));
            dataPacket[3] = (byte)(packetSize & 0xFF);
            dataPacket[4] = (byte)((blockNumber >> 8));
            dataPacket[5] = (byte)(blockNumber & 0xFF);
            System.arraycopy(data, (blockNumber - 1) * 512, dataPacket, 6, packetSize);
            dataQueue.add(dataPacket);
            blockNumber++;
        }
    }


}
