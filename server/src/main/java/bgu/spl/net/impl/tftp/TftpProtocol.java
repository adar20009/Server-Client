package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

class holder {
    static ConcurrentHashMap<Integer, String> ids_login = new ConcurrentHashMap<>();
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    // private ConnectionsImpl<byte[]> connections;
    private Connections<byte[]> connections;
    private int connectionId;
    private boolean shouldTerminate;
    private boolean loggedIn;
    private ConcurrentLinkedQueue<byte[]> dataQueue;
    private String uploadingFileName;
    private int expectedSendBlockNumber;
    private int expectedReceiveBlockNumber;
    private List<Byte> dataRecieved;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        // this.connections = (ConnectionsImpl<byte[]>) connections;
        this.connections = connections;
        this.connectionId = connectionId;
        this.shouldTerminate = false;
        this.loggedIn = false;
        this.dataQueue = new ConcurrentLinkedQueue<>();
        this.uploadingFileName = "";
        this.expectedSendBlockNumber = 1;
        this.expectedReceiveBlockNumber = 1;
        this.dataRecieved = new ArrayList<>();
    }

    @Override
    public void process(byte[] message) {
        // TODO implement this
        short opCode = (short) (((short) message[0]) << 8 | (short) (message[1]));
        String fileNameString = "";
        Path filePath;
        boolean fileExists;
        switch (opCode) {
            case 1:
                // RRQ
                fileNameString = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                filePath = Paths.get("Files", fileNameString);
                fileExists = Files.exists(filePath);

                if (!fileExists) {
                    short errorCode = 1;
                    sendError(errorCode, "File not found - RRQ DELRQ of non-existing file.");
                } else if (!loggedIn) {
                    short errorCode = 6;
                    sendError(errorCode, "User not logged in - Any opcode received before Login completes.");
                }
                else {
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
                        connections.send(connectionId, dataQueue.poll());
                        expectedSendBlockNumber = 1;
                    } catch (Exception e) {}
                }
                break;
            case 2:
                // WRQ
                fileNameString = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                filePath = Paths.get("Files", fileNameString);
                fileExists = Files.exists(filePath);
                if (fileExists) {
                    short errorCode = 5;
                    sendError(errorCode, "File already exists - File name exists on WRQ.");
                } else if (!loggedIn) {
                    short errorCode = 6;
                    sendError(errorCode, "User not logged in - Any opcode received before Login completes.");
                }
                else {
                    uploadingFileName = fileNameString;
                    expectedReceiveBlockNumber = 1;
                    short ackBlockNumber = 0;
                    sendAck(ackBlockNumber);
                }
                break;
            case 3:
                // DATA
                if (!loggedIn) {
                    short errorCode = 6;
                    sendError(errorCode, "User not logged in - Any opcode received before Login completes.");
                }
                else {
                    short packetSize = (short) (( message[2]) << 8 |  (message[3] & 0xFF));
                    short blockNumber = (short) (( message[4]) << 8 |  (message[5] & 0xFF));
                    if (blockNumber == expectedReceiveBlockNumber){
                        if (packetSize == 512) {
                            byte[] data = Arrays.copyOfRange(message, 6, 6 + packetSize);
                            for (byte b : data) {
                                dataRecieved.add(b);
                            }
                            expectedReceiveBlockNumber++;
                            short ackBlockNumber = blockNumber;
                            sendAck(ackBlockNumber);
                        }
                        else {
                            byte[] data = Arrays.copyOfRange(message, 6, 6 + packetSize);
                            for (byte b : data) {
                                dataRecieved.add(b);
                            }
                            byte[] fileBytes = new byte[dataRecieved.size()];
                            for (int i = 0; i < dataRecieved.size(); i++) {
                                fileBytes[i] = dataRecieved.get(i);
                            }
                            try {
                                Files.write(Paths.get("Files", uploadingFileName), fileBytes);
                                short ackBlockNumber = blockNumber;
                                sendAck(ackBlockNumber);
                                sendBRDCST(uploadingFileName, true);
                            } catch (IOException e) {}
                            uploadingFileName = "";
                            expectedReceiveBlockNumber = 1;
                            dataRecieved.clear();
                        }
                    }
                    else {
                        short errorCode = 0;
                        sendError(errorCode, "Illegal TFTP operation - Wrong block number.");
                        System.out.println("Wrong block number received, Upload file operation failed.");
                        uploadingFileName = "";
                        expectedReceiveBlockNumber = 1;
                        dataRecieved.clear();
                    }
                }
                break;
            case 4:
                // ACK
                if (!loggedIn) {
                    short errorCode = 6;
                    sendError(errorCode, "User not logged in - Any opcode received before Login completes.");
                }
                else {
                    short blockNumber = (short) (( message[2] << 8 ) |  (message[3] & 0xFF));
                    if (blockNumber == expectedSendBlockNumber) {
                        if (!dataQueue.isEmpty()) {
                            connections.send(connectionId, dataQueue.poll());
                            expectedSendBlockNumber++;
                        }
                        else {
                            expectedSendBlockNumber = 1;
                            uploadingFileName = "";
                        }
                    }
                    else {
                        short errorCode = 0;
                        sendError(errorCode, "Illegal TFTP operation - Wrong block number.");
                        System.out.println("Wrong block number received, expected" + expectedSendBlockNumber + " but received " + blockNumber + ". Download file operation failed.");
                        expectedSendBlockNumber = 1;
                        dataQueue.clear();
                    }
                }
                break;
            case 5:
                // ERROR
                break;
            case 6:
                // DIRQ
                if (!loggedIn) {
                    short errorCode = 6;
                    sendError(errorCode, "User not logged in - Any opcode received before Login completes.");
                }
                else {
                    Path dirPath = Paths.get("Files");
                    List<String> fileNamesStrings = new ArrayList<>();

                    if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                        try {
                            Files.list(dirPath).forEach(path -> {
                                fileNamesStrings.add(path.getFileName().toString() + '\0');
                            });
                        } catch (IOException e) {}
                    }

                    StringBuilder dataPacketBuilder = new StringBuilder();
                    for (String fileName : fileNamesStrings) {
                        dataPacketBuilder.append(fileName);
                    }
                    dataPacketBuilder.deleteCharAt(dataPacketBuilder.length() - 1);
                    byte[] dataPacket = dataPacketBuilder.toString().getBytes();
                    dataSalami(dataPacket);
                    connections.send(connectionId, dataQueue.poll());
                    expectedSendBlockNumber = 1;
                }
                break;
            case 7:
                // LOGRQ
                String userNameString = new String(message, 2, message.length - 2, StandardCharsets.UTF_8); 
                if (holder.ids_login.containsValue(userNameString)) {
                    short errorCode = 7;
                    sendError(errorCode, "User already logged in - Login username already connected.");
                }
                else if(loggedIn) {
                    short errorCode = 0;
                    sendError(errorCode, "You are already logged in");
                }
                else {
                    holder.ids_login.put(connectionId, userNameString);
                    loggedIn = true;
                    short ackBlockNumber = 0;
                    sendAck(ackBlockNumber);
                }
                break;
            case 8:
                // DELRQ
                fileNameString = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                filePath = Paths.get("Files", fileNameString);
                fileExists = Files.exists(filePath);

                if (!fileExists) {
                    short errorCode = 1;
                    sendError(errorCode, "File not found - RRQ DELRQ of non-existing file.");
                } else if (!loggedIn) {
                    short errorCode = 6;
                    sendError(errorCode, "User not logged in - Any opcode received before Login completes.");
                }
                else {
                    try {
                        Files.delete(filePath);
                        short ackBlockNumber = 0;
                        sendAck(ackBlockNumber);
                        sendBRDCST(fileNameString, false);
                    } catch (Exception e) {}
                }
                break;
            case 10:
                // DISC
                if (!loggedIn) {
                    //short errorCode = 6;
                    //sendError(errorCode, "User not logged in - Any opcode received before Login completes.");
                    //connections.disconnect(connectionId);
                }
                else {
                    loggedIn = false;
                    short ackBlockNumber = 0;
                    sendAck(ackBlockNumber);
                    holder.ids_login.remove(connectionId);
                    connections.disconnect(connectionId);
                    shouldTerminate = true;
                }
                break;
            default:
                break;
        }
    }


    private void sendError(short errorCode, String errorMessage) {
        byte[] error = new byte[5 + errorMessage.length()];
        error[0] = (byte) 0;
        error[1] = (byte) 5;
        error[2] = (byte) ((errorCode >> 8) & 0xFF);
        error[3] = (byte) (errorCode & 0xFF);
        byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(errorMessageBytes, 0, error, 4, errorMessageBytes.length);
        error[4 + errorMessageBytes.length] = 0;
        connections.send(connectionId, error);
    }

    private void sendAck(short blockNumber) {
        byte[] ack = new byte[4];
        short opCode = 4;
        ack[0] = (byte) (opCode >> 8);
        ack[1] = (byte) (opCode & 0xFF);
        ack[2] = (byte) (blockNumber >> 8);
        ack[3] = (byte) (blockNumber & 0xFF);
        connections.send(connectionId, ack);
    }

    private void sendBRDCST(String fileNameString, boolean isAdded) { 
        byte[] brdcst = new byte[4 + fileNameString.length()];
        brdcst[0] = (byte) 0;
        brdcst[1] = (byte) 9;
        if (isAdded)
            brdcst[2] = (byte) 1;
        else
            brdcst[2] = (byte) 0;
        byte[] fileNameBytes = fileNameString.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(fileNameBytes, 0, brdcst, 3, fileNameBytes.length);
        brdcst[3 + fileNameBytes.length] = 0;
        for (Integer connectionId : holder.ids_login.keySet()) {
            connections.send(connectionId, brdcst);
        }
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

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        return shouldTerminate;
    } 


    
}
