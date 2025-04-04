import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Random;

public class Server {
    public static void main(String[] args) throws IOException {
        int port = 69; // TFTP uses port number 69

        DatagramSocket socket = new DatagramSocket(port);
        byte[] buffer = new byte[516];

        System.out.println("Server has started running and listening at port: " + port);

        while (true) {
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

            // Receive initial packet from client
            socket.receive(receivedPacket);

            // create a new server thread, passing a random port number and the initial packet
            port = new Random().nextInt(65_536); // > 1024 ??
            new ServerThread(new DatagramSocket(port), receivedPacket).start();
        }
    }
}
