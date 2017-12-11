import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class Client {
    public static final int CHUNCK_SIZE = 1024;
    private DatagramSocket newSocket;
    private InetAddress serverAddress; // IP address of server
    private int serverPortNo; // server port number
    @SuppressWarnings("unused")
    private int clientPortNo; // client port number
    private String filename; // Filename to be transferred
    @SuppressWarnings("unused")
    private int slidingWindowSize;
    private Vector<Packet> receivedPackets;
    private boolean done = false;
    private boolean requestRecieved;

    public Client() throws IOException {

        BufferedReader reader = new BufferedReader(new FileReader("client.in"));
        String line;
        ArrayList<String> lines = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();

        serverAddress = InetAddress.getByName(lines.get(0));
        serverPortNo = Integer.parseInt(lines.get(1));
        clientPortNo = Integer.parseInt(lines.get(2));
        filename = lines.get(3);
        slidingWindowSize = Integer.parseInt(lines.get(4));

        // initialize packets
        receivedPackets = new Vector<Packet>();

        // create new socket
        newSocket = new DatagramSocket();
        handle_response();
    }

    private void handle_response() {
        try {
            send_request();
            System.out.println("Client send request");
            while (!done) {
                byte[] data = new byte[FileScanner.CHUNK_SIZE + 12];
                DatagramPacket receivedDatagram = new DatagramPacket(data, data.length);
                // System.out.println("client: " + " is waiting for Data");

                newSocket.receive(receivedDatagram);
                // System.out.println("client: " + " has received data ");

                Thread data_handler = new Thread() {
                    public void run() {
                        extract_data(receivedDatagram);

                    }
                };
                data_handler.start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void extract_data(DatagramPacket receivedDatagram) {
        byte[] data = receivedDatagram.getData();

        // System.out.println("data from extract_data: " + data);

        Packet received_pckt = new Packet(data);
        try {
            if (received_pckt.getType() == Packet.PacketType.DATA) {
                System.out.println("Client: Recieved Data");
                boolean exist = false;
                for (Packet p : receivedPackets) {
                    if (p.getSeqNo() == received_pckt.getSeqNo()) {
                        exist = true;
                        break;
                    }
                }
                if (!exist) {
                    receivedPackets.add(received_pckt);
                }

                // send ack if duplicated packet (in case of non corrupted packet)
                int check = received_pckt.getSum() | received_pckt.getActualCheckSum();
                if (check == -1)
                    send_ack(received_pckt, receivedDatagram);
                else
                    System.out.println("Corrupted packet received!");
            } else if (received_pckt.getType() == Packet.PacketType.SIGNAL) {
                if ((Packet.Signal) received_pckt.getBody() == Packet.Signal.STARTING_TRANSMISSION) {
                    System.out.println("Client: Recieved Transmission Started");
                    requestRecieved = true;
                } else {
                    System.out.println("Client: Recieved Transmission Completed");
                    Collections.sort(receivedPackets);

                    String fileName = new Random().nextInt(1000) + filename;
                    FileOutputStream stream = new FileOutputStream(fileName);
                    for (Packet p : receivedPackets) {
                        stream.write((byte[]) p.getBody());
                    }
                    stream.flush();
                    stream.close();
                    done = true;
                    System.out.println("client: " + " has received all the data ");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void send_request() throws IOException {
        send_packet(new Packet(Packet.PacketType.REQ, 0, filename.getBytes()), serverAddress, serverPortNo);
        if (!requestRecieved)
            startTimer();

    }

    private synchronized void startTimer() {
        Timer timeOut = new Timer();
        timeOut.schedule(new TimerTask() {

            @Override
            public void run() {
                if (!requestRecieved) {
                    try {
                        send_request();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        }, 1000);
    }

    private synchronized void send_ack(Packet received_pckt, DatagramPacket receivedDatagram) throws IOException {
        send_packet(new Packet(Packet.PacketType.SIGNAL, received_pckt.getSeqNo(), Packet.Signal.RECEIVED_PACKET_ACK),
                receivedDatagram.getAddress(), receivedDatagram.getPort());
    }

    private synchronized void send_packet(Packet pckt, InetAddress serverAddress, int serverPortNo) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(pckt.getData(), pckt.getData().length, serverAddress,
                serverPortNo);
        newSocket.send(sendPacket);
    }
}