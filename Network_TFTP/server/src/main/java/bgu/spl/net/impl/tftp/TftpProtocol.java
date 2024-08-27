package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

class loggedInUsers {
    static ConcurrentHashMap <Integer, String> LoggedIds = new ConcurrentHashMap<Integer, String>();
}
public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    boolean isLogged = false;
    int connectionId;
    Connections<byte[]> connections;
    private boolean shouldTerminate = false;
    private ConcurrentLinkedQueue<byte[]> packets = new ConcurrentLinkedQueue<>();
    private String filePath = null;
    private byte[] uploadData = new byte[0];
    private byte[] bCastFile = null;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        this.connectionId = connectionId;
        this.connections = connections;
        this.packets = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void process(byte[] message) {
        // TODO implement this
        if (message[0] != 0x0) {
            // ERROR
            error(4, "Illegal TFTP operation");
        }
        else if (message[1] == 1) {
            // RRQ
            if (!isLogged) {
                // send error
                error(6, "User not logged in");
            }
            else {
                byte[] fileName = new byte[message.length - 3];
                System.arraycopy(message, 2, fileName, 0, message.length - 3);
                String name = new String(fileName);
                try {
                    File file = new File("Files", name);
                    if (file.exists()) {
                        RRQ(name);
                    }
                    else
                        error(1, "File not found");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        else if (message[1] == 2) {
            // WRQ
            if (!isLogged) {
                // send error
                error(6, "User not logged in");
            }
            else {
                bCastFile = new byte[message.length - 3];
                System.arraycopy(message, 2, bCastFile, 0, message.length - 3);
                String name = new String(bCastFile);
                try {
                    File file = new File("Files", name);
                    if (!file.exists()) {
                        filePath = "Files" + File.separator + name;
                        sendAck((short)0);
                    }
                    else {
                        error(5, "File already exists");
                        bCastFile = null;
                    }
                } catch (Exception e) {
                    error(1, "File not found");
                }
            }
        }

        else if (message[1] == 3) {
            // DATA
            inData(message);
        }

        else if (message[1] == 4) {
            // ACK
            // check if we have more packets
            if (!packets.isEmpty()) {
                byte[] packet = packets.poll();
                connections.send(connectionId, packet);
            }
        }

        else if (message[1] == 6) {
            // DIRQ
            if (!isLogged) {
                // send error
                error(6, "User not logged in");
            }
            else {
                dirQ();
            }
        }

        else if (message[1] == 7) {
            // LOGRQ
            // check if user logged in
            if (isLogged) {
                // send error
                error(7, "User already logged in");
            }
            else {
                // send ack
                logIn(message);
            }
        }
        else if (message[1] == 8) {
            // DELRQ
            if (!isLogged) {
                // send error
                error(6, "User already logged in");
            }
            else {
                delRQ(message);
            }
        }
        else if (message[1] == 10) {
            // DISC
            // check if user logged in
            if (isLogged) {
                // send ack
                logOut();
            }
            else {
                // send error
                error(6, "User not logged in"); 
                connections.disconnect(connectionId);
            }
        }
        else {
            // ERROR
            error(4, "Illegal TFTP operation");
            }
        }
    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        return shouldTerminate;
    } 

    public void error(int errorCode, String errorMsg) {
        byte[] error = new byte[4 + errorMsg.length() + 1];
        error[0] = 0;
        error[1] = 5;
        error[2] = 0;
        error[3] = (byte) errorCode;
        for (int i = 0; i < errorMsg.length(); i++) {
            error[i + 4] = (byte) errorMsg.charAt(i);
        }
        error[error.length - 1] = 0;
        connections.send(connectionId, error);
    }
    public void logIn(byte[] message) {
        String name = new String(message, 2, message.length - 3);
        if (loggedInUsers.LoggedIds.containsValue(name)) {
            error(7, "UserName already logged in");
            return;
        }
        else {
            isLogged = true;
            loggedInUsers.LoggedIds.put(connectionId, name);
            sendAck((short)0);
        }
    }

    public void logOut() {
        isLogged = false;
        loggedInUsers.LoggedIds.remove(connectionId);
        sendAck((short)0);
        connections.disconnect(connectionId);
        shouldTerminate = true;
    }
    public void sendAck(short blockNum) {
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = 4;
        ack[2] = (byte) (blockNum >> 8);
        ack[3] = (byte) (blockNum & 0xff);
        connections.send(connectionId, ack);
    }

    public void RRQ(String name) {
        // send ack
        // send data
        packetPackage(name);
        byte[] firstPacket = packets.poll();
        connections.send(connectionId, firstPacket);
    }

    public void packetPackage(String fileName) {
        try {
            byte[] fileData = Files.readAllBytes(Paths.get("Files", fileName));
            short numOfPackets = (short) (fileData.length / 512);
            short lastPacketSize = (short) (fileData.length % 512);
            for (short i = 0; i < numOfPackets; i++) {
                byte[] packet = new byte[518];
                short size = 512;
                packet[0] = 0;
                packet[1] = 3;
                packet[2] = (byte) (size >> 8);
                packet[3] = (byte) (size & 0x00ff);
                packet[4] = (byte) ((i+1) >> 8);
                packet[5] = (byte) ((i+1) & 0x00ff);
                System.arraycopy(fileData, i * 512, packet, 6, 512);
                packets.add(packet);
            }
            if (lastPacketSize != 0) {
                byte[] packet = new byte[6 + lastPacketSize];
                short size = (short) (lastPacketSize);
                packet[0] = 0;
                packet[1] = 3;
                packet[2] = (byte) (size >> 8);
                packet[3] = (byte) (size & 0xff);
                packet[4] = (byte) ((numOfPackets+1) >> 8);
                packet[5] = (byte) ((numOfPackets+1) & 0xff);
                System.arraycopy(fileData, numOfPackets * 512, packet, 6, lastPacketSize);
                packets.add(packet);
            }
            else { // empty packet to indicate end of file in case of file's data filled in exactly 512 bytes of data packets
                byte[] packet = new byte[6];
                packet[0] = 0;
                packet[1] = 3;
                packet[2] = 0;
                packet[3] = 0;
                packet[4] = (byte) ((numOfPackets+1) >> 8);
                packet[5] = (byte) ((numOfPackets+1) & 0xff);
                packets.add(packet);
            }
        } catch (Exception e) {
            error(1, "File not found");
        }
    }
    public void inData (byte[] message) {
        short block = (short) (((short) message[4]) << 8 | (short) (message[5]) & 0x00ff);
        if (uploadData.length == 0) {
            uploadData = new byte[message.length - 6];
            System.arraycopy(message, 6, uploadData, 0, message.length - 6);
        }
        else {
            if (message.length > 6) {
                byte[] fileData = new byte[uploadData.length + (message.length - 6)];
                System.arraycopy(uploadData, 0, fileData, 0, uploadData.length);
                System.arraycopy(message, 6, fileData, uploadData.length, message.length - 6);
                uploadData = fileData;
            }
        }
        try {
            if (message.length < 518) {
                File newFile = new File(filePath);
                FileOutputStream outputStream = new FileOutputStream(filePath);
                outputStream.write(uploadData);
                outputStream.close();
                sendAck(block);
                uploadData = new byte[0];
                filePath = null;
                bCast((short) 1);
            }
            else
                sendAck(block);
        } catch (Exception e) {
            error(2, "Access violation");
        }
    }
    public void bCast(short type) {
        // bcast a message to all logged in users
        byte[] bcast = new byte[bCastFile.length + 4];
        bcast[0] = 0;
        bcast[1] = 9;
        bcast[2] = (byte) (type);
        System.arraycopy(bCastFile, 0, bcast, 3, bCastFile.length);
        bcast[bcast.length - 1] = 0;
        for (int id : loggedInUsers.LoggedIds.keySet()) {
            connections.send(id, bcast);
        }
        bCastFile = null;
    }

    public void dirQ() {
        File dir = new File("Files");
        String[] files = dir.list();
        String filesStr = "";
        for (String file : files) {
            filesStr += file + '0';
        }
        String filesList = filesStr.substring(0, filesStr.length() - 1);
        dirqPackage(filesList);
        connections.send(connectionId, packets.poll());
    }
    public void delRQ (byte[] message) {
        byte[] fileName = new byte[message.length - 3];
        System.arraycopy(message, 2, fileName, 0, message.length - 3);
        String name = new String(fileName);
        File file = new File("Files", name);
        try {
            if (file.exists()) {
                file.delete();
                sendAck((short)0);
                bCastFile = fileName;
                bCast((short) 0);
            } else {
                error(1, "File not found");
            }
        } catch (Exception e) {
            error(2, "Access violation");
        }
    }

    public void dirqPackage(String filesNames) {
        try {
            byte[] dirqFiles = filesNames.getBytes();
            short numOfPackets = (short) (dirqFiles.length / 512);
            short lastPacketSize = (short) (dirqFiles.length % 512);
            for (short i = 0; i < numOfPackets; i++) {
                byte[] packet = new byte[518];
                short size = 512;
                packet[0] = 0;
                packet[1] = 3;
                packet[2] = (byte) (size >> 8);
                packet[3] = (byte) (size & 0xff);
                packet[4] = (byte) ((i+1) >> 8);
                packet[5] = (byte) ((i+1) & 0xff);
                System.arraycopy(dirqFiles, i * 512, packet, 6, 512);
                packets.add(packet);
            }
            if (lastPacketSize != 0) {
                byte[] packet = new byte[6 + lastPacketSize];
                short size = (short) (lastPacketSize);
                packet[0] = 0;
                packet[1] = 3;
                packet[2] = (byte) (size >> 8);
                packet[3] = (byte) (size & 0xff);
                packet[4] = (byte) ((numOfPackets+1) >> 8);
                packet[5] = (byte) ((numOfPackets+1) & 0xff);
                System.arraycopy(dirqFiles, numOfPackets * 512, packet, 6, lastPacketSize);
                packets.add(packet);
            }
            else { // empty packet to indicate end of transfer in case of file's names data filled in exactly 512 bytes of data packets
                byte[] packet = new byte[6];
                packet[0] = 0;
                packet[1] = 3;
                packet[2] = 0;
                packet[3] = 0;
                packet[4] = (byte) ((numOfPackets+1) >> 8);
                packet[5] = (byte) ((numOfPackets+1) & 0xff);
                packets.add(packet);
            }
        } catch (Exception e) {
            error(2, "Access violation");
        }
    }
}
