import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Server extends Thread {
    private final DatagramSocket socket;
    private final int port = 69; // TFTP uses 69, which is below 1024 and may not work without administrator rights

    // 2 byte opcode / operation
    private final short RRQ = 1;
    private final short WRQ = 2;
    private final short DATA = 3;
    private final short ACK = 4;
    private final short ERROR = 5;

    // 2 byte error code
    private final short ERR_FNF = 1;

    private byte[] buffer = new byte[516];
    private DatagramPacket receivedPacket;
    private InetAddress clientAddress;
    private int clientPort;

    private File file;
    private String fileName;
    private final String path = "src/main/java/";

    public static void main(String[] args) throws SocketException {
        new Server().start();
    }

    public Server() throws SocketException {
        // TODO create a new thread every time client connects, look at TCP lab
        socket = new DatagramSocket(port);
    }

    public void run() {
        System.out.println("Server has started running and listening at port: " + port);

        while (true) {
            receivedPacket = new DatagramPacket(buffer, buffer.length);

            // Receive packet from client
            try {
                socket.receive(receivedPacket);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Get the address and port
            clientAddress = receivedPacket.getAddress();
            clientPort = receivedPacket.getPort();

            // Get the opcode
            short opcode = parseOpcode(receivedPacket);

            // Determine the request type
            if (opcode == RRQ || opcode == WRQ) {
                parseRequestPacket(receivedPacket);

                file = new File(path + fileName);

                if (opcode == RRQ) {
                    System.out.println("Read request from " + clientAddress.getHostName() + " at port: " + clientPort);
                    try {
                        HandleReadRequest();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    System.out.println("Write request from " + clientAddress.getHostName() + " at port: " + clientPort);
                    try {
                        HandleWriteRequest();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            else {
                System.err.println("Received unknown packet. Disregarding packet. (1)");
            }
        }
    }

    /**
     * Sends the file to the client in data packets and waits for acknowledgement packets in return
     */
    private void HandleReadRequest() throws IOException {
        if (!file.exists()) {
            System.err.println("Requested file not found. Sending error packet.");
            DatagramPacket ERRORPacket = constructErrorPacket(ERR_FNF, "Requested file not found.", clientAddress, clientPort);
            socket.send(ERRORPacket);
            return;
        }

        FileInputStream inputStream = new FileInputStream(file);

        byte[] dataBuffer = new byte[512];
        int bytesRead;
        short blockNumber = 1;

        while (true) {
            // Read up to 512 bytes from the file and store in the data buffer
            bytesRead = inputStream.read(dataBuffer);
            if (bytesRead == -1) {
                dataBuffer = new byte[0];
            }
            else {
                dataBuffer = Arrays.copyOf(dataBuffer, bytesRead);
            }

            // Create a data packet from the data buffer and the current block number
            DatagramPacket DATAPacket = constructDataPacket(blockNumber, dataBuffer, clientAddress, clientPort);
            socket.send(DATAPacket);

            System.out.println("Sent DATA packet with block number: " + blockNumber + " and " + bytesRead + " bytes of data");

            if (bytesRead < 512) {
                System.out.println("Finished writing file to client.");
                inputStream.close();
                break;
            }

            receivedPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivedPacket);

            // check opcode and block number of received packet
            short receivedBlockNumber = parseBlockNumber(receivedPacket);
            if (parseOpcode(receivedPacket) == ACK && receivedBlockNumber == blockNumber) {
                System.out.println("Success, received ACK packet with block number: " + receivedBlockNumber);
                blockNumber++;
            }
            else {
                System.err.println("Received unknown packet. Disregarding packet. (2)");
            }
        }
    }

    /**
     * Sends acknowledgement packets to the client and received data packets, writing the data into a new file
     */
    private void HandleWriteRequest() throws IOException {
        short blockNumber = 0;

        // Create an acknowledgement packet to send back to the client
        DatagramPacket ACKPacket = constructACKPacket(blockNumber, clientAddress, clientPort);
        socket.send(ACKPacket);

        DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(file));

        do {
            // receive data packet from client
            receivedPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivedPacket);

            short receivedBlockNumber = parseBlockNumber(receivedPacket);
            if (parseOpcode(receivedPacket) == DATA && receivedBlockNumber == blockNumber + 1) {
                System.out.println("Success, received DATA packet with block number: " + receivedBlockNumber);
                outputStream.write(receivedPacket.getData(), 4, receivedPacket.getLength() - 4);

                // Send ACK packet to client with the same block number as the data packet
                ACKPacket = constructACKPacket(receivedBlockNumber, receivedPacket.getAddress(), receivedPacket.getPort());
                socket.send(ACKPacket);
                System.out.println("Sent ACK packet with block number: " + receivedBlockNumber);

                blockNumber = receivedBlockNumber;
            }
            else {
                System.err.println("Received block number incorrect, duplicate packet?");
            }
        }
        while (receivedPacket.getLength() >= 512);

        System.out.println("Finished reading file from client.");
        outputStream.close();
    }

    /**
     * Creates a packet following the TFTP data packet format
     * @param blockNumber The block number of the packet
     * @param data The actual data to send
     * @param address The address to send the packet to
     * @param port The port to send the packet to
     * @return A datagram packet
     */
    private DatagramPacket constructDataPacket(short blockNumber, byte[] data, InetAddress address, int port) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeShort(DATA);
        out.writeShort(blockNumber);
        out.write(data);
        out.close();

        byte[] buffer = baos.toByteArray();
        return new DatagramPacket(buffer, buffer.length, address, port);
    }

    /**
     * Creates a packet following the TFTP acknowledgement packet format
     * @param blockNumber The block number of the packet
     * @param address The address to send the packet to
     * @param port The port to send the packet to
     * @return A datagram packet
     */
    private DatagramPacket constructACKPacket(short blockNumber, InetAddress address, int port) {
        int ACKPacketSize = 4;
        ByteBuffer byteBuffer = ByteBuffer.allocate(ACKPacketSize);
        byteBuffer.putShort(ACK);
        byteBuffer.putShort(blockNumber);
        return new DatagramPacket(byteBuffer.array(), ACKPacketSize, address, port);
    }


    /**
     * Creates a packet following the TFTP error packet format
     * @param errorCode The error code
     * @param errorMessage The error message
     * @param address The address to send the packet to
     * @param port The port to send the packet to
     * @return A datagram packet
     */
    private DatagramPacket constructErrorPacket(short errorCode, String errorMessage, InetAddress address, int port) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeShort(ERROR);
        out.writeShort(errorCode);
        out.write(errorMessage.getBytes());
        out.write(0);
        out.close();

        byte[] buffer = baos.toByteArray();
        return new DatagramPacket(buffer, buffer.length, address, port);
    }

    /**
     * Loops through the packet and reads the file name
     * @param packet The TFTP request packet
     */
    private void parseRequestPacket(DatagramPacket packet) {
        byte[] buffer = packet.getData();

        int delimiter = - 1;

        // start at 2, because we know the first 2 bytes will contain opcode
        for (int i = 2; i < buffer.length; i++) {
            if (buffer[i] == 0) { // loop until it finds first 0
                delimiter = i;
                break;
            }
        }

        fileName = new String(buffer, 2, delimiter - 2);
    }

    /**
     * Reads the two byte opcode at the start of all TFTP packets
     * @param packet The TFTP packet
     * @return short value representing the opcode
     */
    private short parseOpcode(DatagramPacket packet) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
        return byteBuffer.getShort();
    }

    /**
     * Reads the two byte block number in certain TFTP packets
     * @param packet The TFTP packet
     * @return short value representing the opcode
     */
    private short parseBlockNumber(DatagramPacket packet) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
        return byteBuffer.getShort(2);
    }
}

