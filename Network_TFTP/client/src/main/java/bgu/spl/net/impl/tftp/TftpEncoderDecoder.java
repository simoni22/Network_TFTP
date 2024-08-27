package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder
    private byte[] bytes = new byte[1 << 10]; // 1k bytes array
    private int len = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        // Append byte to bytes array
        if (len < bytes.length) {
            bytes[len++] = nextByte;
        }
        else {
            // Reallocate bytes array if full
            bytes = Arrays.copyOf(bytes, bytes.length * 2);
            bytes[len++] = nextByte;
        }
        // Check if message is complete
        if (isMessageComplete()) {
            byte[] decodedMessage = Arrays.copyOf(bytes, len);
            len = 0; // Reset for next message
            bytes = new byte[1 << 10]; // Reset for next message
            return decodedMessage;
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return new byte[0];
    }


    public byte[] encode(String message, int opCode) {
        //TODO: implement this
        String command;
        byte[] packet;
        switch (opCode) {
            case 1: // RRQ (Read Request)
            case 2: // WRQ (Write Request)
                // Opcode, filename (null-terminated string), and mode (null-terminated string) required
                command = message.substring(4);
                packet = new byte[command.length() + 3]; // 2 bytes for opcode, 1 byte for null-terminator, 2 bytes for mode, and 2 bytes for null-terminator
                packet[0] = 0; // Opcode
                packet[1] = (byte) opCode; // Opcode
                System.arraycopy(command.getBytes(), 0, packet, 2, command.length());
                packet[packet.length - 1] = 0; // Null-terminator
                return packet;

            case 7: // LOGRQ
            case 8: // DELRQ
                    command = message.substring(6);
                    packet = new byte[command.length() + 3]; // 2 bytes for opcode, 1 byte for null-terminator
                    packet[0] = 0; // Opcode
                    packet[1] = (byte) opCode; // Opcode
                    System.arraycopy(command.getBytes(), 0, packet, 2, command.length());
                    packet[packet.length - 1] = 0; // Null-terminator
                    return packet;

            case 6: // DIRQ
            case 10: //DISC
                packet = new byte[2]; // 2 bytes for opcode
                packet[0] = 0; // Opcode
                packet[1] = (byte) opCode; // Opcode
                return packet;
            default:
                System.out.println("Unknown opcode");
                return null;
        }
    }

    public boolean isMessageComplete() {
        if (len < 2) { // Minimum size for any packet (opcode)
            return false;
        }
        // Extract opcode from the first two bytes
        short opcode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]));
        switch (opcode) {
            case 3: // DATA
                // Opcode, block number (2 bytes), and minimum of 0 bytes of data required
                if (len >= 6) {
                    short packetSize = (short) (((short) bytes[2]) << 8 | (short) (bytes[3]) & 0x00ff);
                    return len == (packetSize + 6); // Check for correct packet size
                }
                return false;
            case 4: // ACK
                // Opcode and block number required
                return len == 4; // Check for correct packet size
            case 5: // ERROR
                // Opcode, error code, and error message (null-terminated string) required
                if (len >= 5)
                    return bytes[len - 1] == 0;
                return false;// Check for null-terminated strings
            case 9: //BCAST
                if (len >= 4)
                    return bytes[len - 1] == 0; // Check for null-terminated strings
                return false;
            default:
                // Unknown opcode, consider incomplete
                return false; // might return true to handle this error on process method in the protocol
        }
    }
}