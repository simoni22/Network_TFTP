package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TftpProtocol implements MessagingProtocol<byte[]> {
    private boolean shouldTerminate = false;
    private boolean isLogged = false;
    private String activeFile = null;
    private int activeOpCode = 0;
    private ConcurrentLinkedQueue<byte[]> uploadData = new ConcurrentLinkedQueue<>(); // for WRQ
    private byte[] downloadData = new byte[0]; // for RRQ/DIRQ


    @Override
    public byte[] process(byte[] msg) {
        int opCode = msg[1];
        String output;
        switch (opCode) {
            case 3://DATA
                if (activeOpCode == 1)
                    return inData(msg);
                else
                    return dirQ(msg);

            case 4: //ACK
                return handleAck(msg);

            case 5: //ERROR

                if (msg.length > 5) {
                    output = new String(msg, 4, msg.length - 5);
                }
                else
                    output = "";
                System.out.println("ERROR " + msg[3] + " " + output);
                if (activeOpCode == 10) {
                    isLogged = false;
                    shouldTerminate = true;
                }
                activeOpCode = 0;
                activeFile = null;
                return null;

            case 9: //BCAST
                output = new String(msg, 3, msg.length - 4);
                if (msg[2] == 0) {
                    System.out.println("BCAST del " + output);
                } else {
                    System.out.println("BCAST add " + output);
                }
                return null;
            default:
                return null;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
    public void setActiveFile(String activeFileName) {
        this.activeFile = activeFileName;
    }
    public void setActiveOpCode(int activeOpCode) {
        this.activeOpCode = activeOpCode;
    }
    public byte[] inData(byte[] data) {
        short block = (short) (((short) data[4]) << 8 | (short) (data[5]) & 0x00ff);
        if (downloadData.length == 0) {
            downloadData = new byte[data.length - 6];
            System.arraycopy(data, 6, downloadData, 0, data.length - 6);
        }
        else {
            if (data.length > 6) {
                byte[] fileData = new byte[downloadData.length + (data.length - 6)];
                System.arraycopy(downloadData, 0, fileData, 0, downloadData.length);
                System.arraycopy(data, 6, fileData, downloadData.length, data.length - 6);
                downloadData = fileData;
            }
        }
        try {
            if (data.length < 518) {
                File newFile = new File(activeFile);
                FileOutputStream outputStream = new FileOutputStream(activeFile);
                outputStream.write(downloadData);
                outputStream.close();
                downloadData = new byte[0];
                activeFile = null;
                activeOpCode = 0;
                System.out.println("RRQ " + newFile.getName() + " complete");
                return buildAck(block);
            }
            else
                return buildAck(block);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public byte[] buildAck(short blockNum) {
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = 4;
        ack[2] = (byte) (blockNum >> 8);
        ack[3] = (byte) (blockNum & 0x00ff);
        return ack;
    }
    public byte[] dirQ(byte[] msg) {
        short block = (short) (((short) msg[4]) << 8 | (short) (msg[5]) & 0x00ff);
        if (downloadData.length == 0) {
            downloadData = new byte[msg.length - 6];
            System.arraycopy(msg, 6, downloadData, 0, msg.length - 6);
        }
        else {
            if (msg.length > 6) {
                byte[] dirFiles = new byte[downloadData.length + (msg.length - 6)];
                System.arraycopy(downloadData, 0, dirFiles, 0, downloadData.length);
                System.arraycopy(msg, 6, dirFiles, downloadData.length, msg.length - 6);
                downloadData = dirFiles;
            }
        }
        if (msg.length < 518) {
            String files = new String(downloadData);
            String[] output = files.split("0");
            activeOpCode = 0;
            downloadData = new byte[0];
            //print each file name in a new line
            for (String file : output) {
                System.out.println(file);
            }
        }
        return buildAck(block);
    }
    public void packetPackage(String filePath) {
        try {
            byte[] fileData = Files.readAllBytes(Paths.get("." + File.separator, filePath));
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
                uploadData.add(packet);
            }
            if (lastPacketSize != 0) {
                byte[] packet = new byte[6 + lastPacketSize];
                short size = (short) (lastPacketSize);
                packet[0] = 0;
                packet[1] = 3;
                packet[2] = (byte) (size >> 8);
                packet[3] = (byte) (size & 0x00ff);
                packet[4] = (byte) ((numOfPackets+1) >> 8);
                packet[5] = (byte) ((numOfPackets+1) & 0x00ff);
                System.arraycopy(fileData, numOfPackets * 512, packet, 6, lastPacketSize);
                uploadData.add(packet);
            }
            else { // empty packet to indicate end of file in case of file's data filled in exactly 512 bytes of data packets
                byte[] packet = new byte[6];
                packet[0] = 0;
                packet[1] = 3;
                packet[2] = 0;
                packet[3] = 0;
                packet[4] = (byte) ((numOfPackets+1) >> 8);
                packet[5] = (byte) ((numOfPackets+1) & 0x00ff);
                uploadData.add(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public boolean isLogged() {
        return isLogged;
    }
    public byte[] handleAck(byte[] msg) {
        short block = (short) (((short) msg[2]) << 8 | (short) (msg[3]) & 0x00ff);
        if (activeOpCode == 2) { //WRQ
            if (block == 0) {
                packetPackage(activeFile);
                System.out.println("ACK " + block);
                return uploadData.poll();
            }
            else {
                if (uploadData.isEmpty()) {
                    activeOpCode = 0;
                    activeFile = null;
                    System.out.println("ACK " + block);
                    return null;
                }
                System.out.println("ACK " + block);
                return uploadData.poll();
            }
        }
        else if (activeOpCode == 7) { //LOGRQ
            isLogged = true;
            activeOpCode = 0;
            System.out.println("ACK " + block);
            return null;
        }
        else if (activeOpCode == 10) { //DISC
            isLogged = false;
            System.out.println("ACK " + block);
            shouldTerminate = true;
            return null;
        }
        else {
            System.out.println("ACK " + block);
            return null;
        }
    }
    public void terminate() {
        shouldTerminate = true;
    }

}
