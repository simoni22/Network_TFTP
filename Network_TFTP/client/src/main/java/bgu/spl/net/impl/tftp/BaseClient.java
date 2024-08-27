package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.io.IOException;

public class BaseClient {
    private int port;
    private TftpProtocol protocol;
    private TftpEncoderDecoder encdec;
    private Socket sock;
    private boolean shouldTerminate = false;
    private Thread keyBoardThread;
    private Thread listeningThread;
    private BufferedOutputStream out;
    private BufferedInputStream in;
    private int activeOpCode = 0;
    private String activeFileName = "";
    private BufferedReader userIn;
    private boolean firstCommand = false;

    public BaseClient(int port, TftpProtocol protocol, TftpEncoderDecoder encdec) {
        this.port = port;
        this.protocol = protocol;
        this.encdec = encdec;
        this.sock = null;
        this.userIn = new BufferedReader
                (new InputStreamReader(System.in));
    }

    public void run(String serverName){
        try (Socket sock = new Socket(serverName, port)) {
            this.sock = sock;
            this.out = new BufferedOutputStream(sock.getOutputStream());
            this.in = new BufferedInputStream(sock.getInputStream());
            this.keyBoardThread = Thread.currentThread();
            this.listeningThread = new Thread(listener);
            String line = null;
            while (!protocol.shouldTerminate() && !shouldTerminate) {
                while (!protocol.shouldTerminate() && (line = userIn.readLine()) != null) {
                    int opCode = processLine(line);
                    if (opCode == -2) {
                        shouldTerminate = true;
                        close();
                        break;
                    }
                    if (!firstCommand) {
                        firstCommand = true;
                        this.listeningThread.start();
                    }
                    if (opCode != -1) {
                        byte[] packet = encdec.encode(line, opCode);
                        if (opCode == 1) {
                            this.protocol.setActiveFile("." + File.separator + line.substring(4));
                            this.protocol.setActiveOpCode(opCode);
                        }
                        else if (opCode == 2) {
                            this.protocol.setActiveFile(line.substring(4));
                            this.protocol.setActiveOpCode(opCode);
                        }
                        else if (opCode == 6 || opCode == 7 || opCode == 10) {
                            this.protocol.setActiveOpCode(opCode);
                        }
                        if (packet != null) {
                            send(packet);
                        }
                        if (opCode == 10) {
                            listeningThread.interrupt();
                            listeningThread.join();
                            //shouldTerminate = true;
                            //break;
                        }
                    }
                }
                /*if (protocol.shouldTerminate() || shouldTerminate) {
                    break;
                }*/
            }
            close();
        }catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public synchronized void send (byte[] message) {
        try {
            out.write(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Runnable listener = () -> {
        int read;
        try {
            while (!protocol.shouldTerminate() && !shouldTerminate) {
                if (!shouldTerminate && (read = in.read()) >= 0) {
                    byte[] nextMessage = encdec.decodeNextByte((byte) read);
                    if (nextMessage != null) {
                        byte[] response = protocol.process(nextMessage);
                        if (response != null) {
                            send(response);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    };
    public TftpProtocol getProtocol() {
        return protocol;
    }
    public TftpEncoderDecoder getEncDec() {
        return encdec;
    }
    public int processLine(String line) {
        String[] words = line.split(" "); //separate the string to words
        if (words[0].equals("LOGRQ") && words.length > 1) {
            return 7;
        }
        else if (line.equals("DISC")) {
            if (protocol.isLogged())
                return 10;
            return -2;
        }
        else if (line.equals("DIRQ")) {
            return 6;
        }
        else if (words[0].equals("RRQ") && words.length > 1) {
            //check if file exists
            String pathName = "." + File.separator + line.substring(4);
            File file = new File(pathName);
            if (!file.exists())
                return 1;
            else {
                System.out.println("file already exist");
                return -1;
            }
        }
        else if (words[0].equals("WRQ") && words.length > 1) {
            String pathName = "." + File.separator + line.substring(4);
            File file = new File(pathName);
            if (file.exists())
                return 2;
            else {
                System.out.println("file does not exist");
                return -1;
            }
        }
        else if (words[0].equals("DELRQ") && words.length > 1) {
            return 8;
        }
        else {
            System.out.println("invalid command");
            return -1;
        }
    }
    public void close() {
        try {
            shouldTerminate = true;
            this.sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
