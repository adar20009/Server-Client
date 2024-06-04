package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;


public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            args = new String[]{"localhost", "7777"};
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        try (Socket sock = new Socket(args[0], Integer.parseInt(args[1]))) {
            System.out.println("Connected to server");
            BufferedInputStream in = new BufferedInputStream((sock.getInputStream()));
            BufferedOutputStream out = new BufferedOutputStream((sock.getOutputStream()));
            TftpConnectionHandler handler = new TftpConnectionHandler(in, out);
            TftpKeyboard keyboard = new TftpKeyboard(handler);
            Thread keyboardThread = new Thread(keyboard);
            Thread listenerThread = new Thread(new TftpListener(handler, keyboard));
            keyboardThread.start();
            listenerThread.start();
            try{
                listenerThread.join();
                keyboardThread.join();
            } catch (Exception e) {}
        } catch (Exception e) {System.out.println("DFsdf");}
    }
}
