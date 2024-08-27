package bgu.spl.net.impl.tftp;
public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) {
        BaseClient client = new BaseClient(7777,
                new TftpProtocol(),
                new TftpEncoderDecoder()
        );
        client.run("localhost");
    }
}
