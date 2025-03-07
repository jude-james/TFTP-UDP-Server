import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Server extends Thread {
    private DatagramSocket socket;
    private boolean running;
    private byte[] buffer = new byte[256];

    private final int port = 69; // TFTP uses 69, which is below 1024 and may not work without administrator rights

    // 2 byte opcode / operation
    private short RRQ = 1;
    private short WRQ = 2;
    private short DATA = 3;
    private short ACK = 4;
    private short ERROR = 5;

    /**
     * example javadoc
     * @param args args
     */
    public static void main(String[] args) throws SocketException {
        new Server().start();
    }

    public Server() throws SocketException {
        socket = new DatagramSocket(port);
    }

    public void run() {
        System.out.println("Server has started running and listening at port: " + port);

        running = true;

        while (running) {
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

            // Receive packet from client
            try {
                socket.receive(receivedPacket);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

            InetAddress clientAddress = receivedPacket.getAddress();
            int clientPort = receivedPacket.getPort();

            // determine the request (RRQ, WRQ etc)

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer));
            try {
                short opcode = in.readShort();

                if (opcode == RRQ || opcode == WRQ) {
                    System.out.println("Read/Write request from " + clientAddress.getHostName() + " at port:" + clientPort);

                    int delimiterIndex = - 1;

                    // loop through until hit 0, store that index as the end of the file name, then do the same for mode

                    // start at 2, because we know the first 2 bytes will contain opcode
                    for (int i = 2; i < buffer.length; i++) {
                        if (buffer[i] == 0) { // we've hit the first 0
                            delimiterIndex = i;
                            break;
                        }
                    }

                    String fileName = new String(buffer, 2, delimiterIndex - 2);
                    System.out.println(fileName);

                    for (int i = delimiterIndex; i < buffer.length; i++) {
                        if (buffer[i] == 0) { // we've hit the second 0
                            delimiterIndex = i;
                            break;
                        }
                    }

                    String mode = new String(buffer, 2 + fileName.length()+1, delimiterIndex - 2 + fileName.length()-5); // -5?
                    System.out.println(mode);

                    if (opcode == RRQ) {
                        // TODO send DATA with block number 1 back to client
                        System.out.println("Read request initial connection...");
                        byte[] signal = "ACK".getBytes();
                        receivedPacket = new DatagramPacket(signal, signal.length, clientAddress, clientPort);
                        socket.send(receivedPacket);
                    }
                    else if (opcode == WRQ) {
                        // TODO send ACK with block number 0 back to client
                    }
                }
                else {
                    // TODO deal with ERROR, ACK...
                }


            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

            //ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            //short opcode = byteBuffer.getShort();

            // TODO This is where server should check the packet headers, the ack number?, and what type of
            // request the user is making?
            // put logic into a thread? to allow for server to handle multiple clients

            // get address, port, and length of packet from the client

            /*
            // assign a new packet with an address and port, to send back to the client
            receivedPacket = new DatagramPacket(buffer, receivedLength, clientAddress, port);
            String received = new String(receivedPacket.getData(), 0, receivedPacket.getLength());

            try {
                socket.send(receivedPacket);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

             */
        }
    }
}
