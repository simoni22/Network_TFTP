package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;

public class TftpServer {
    //TODO: Implement this
    public static void main(String[] args) {
        Server.threadPerClient(
                7777,
                () -> new TftpProtocol(),
                TftpEncoderDecoder::new
        ).serve();
    }
}
