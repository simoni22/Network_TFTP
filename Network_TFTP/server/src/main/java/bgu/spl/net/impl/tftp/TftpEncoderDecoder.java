package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder
    private byte[] bytes = new byte[1 << 10]; // 1k bytes array
    private int len = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        // TODO: implement this
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
        //TODO: implement this
        return message;
    }

    public boolean isMessageComplete() {
        if (len < 2) { // Minimum size for any packet (opcode)
            return false;
        }
        // Extract opcode from the first two bytes
        short opcode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0x00ff);
        switch (opcode) {
            case 1: // RRQ (Read Request)
            case 2: // WRQ (Write Request)
                // Opcode, filename (null-terminated string), and mode (null-terminated string) required
                return bytes[len - 1] == 0; // Check for null-terminated strings
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
            case 6: // DIRQ
                return len == 2;
            case 7: // LOGRQ
            case 8: // DELRQ
                return bytes[len - 1] == 0; // Check for null-terminated strings
            case 10 : //DISC
                return len == 2; // Check for correct packet size
            default:
                // Unknown opcode, consider incomplete
                return false; // might return true to handle this error on process method in the protocol
        }
    }
}