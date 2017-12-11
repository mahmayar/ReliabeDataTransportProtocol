import java.awt.Point;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class Server {
    private int threeDupAckCnt = 0;
    private int serverPortNo; // server port number
    private int maxSlidingWindowSize;
    private long randomSeed;
    private float p;
    private Random rnd;
    private int timeOut = 1000;
    private DatagramSocket newSocket;
    private FileScanner file;
    private Map<Integer, Vector<Packet.State>> packetStates = new HashMap<Integer, Vector<Packet.State>>();
    private Map<Integer, DatagramSocket> sockets = new HashMap<Integer, DatagramSocket>();
    private Map<Integer, Boolean> transmissionCompleted = new HashMap<Integer, Boolean>();
    private Map<Integer, FileScanner> files = new HashMap<Integer, FileScanner>();
    private Map<Integer, Point> processWndSize = new HashMap<Integer, Point>();
    private ForkJoinPool forkJoinPool;
    private BufferedWriter writer;

    public Server(String fileName) throws Exception {
        writer = new BufferedWriter(new FileWriter("window.txt"));
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        String line;
        ArrayList<String> lines = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();
        serverPortNo = Integer.parseInt(lines.get(0));
        maxSlidingWindowSize = Integer.parseInt(lines.get(1));
        randomSeed = Long.parseLong(lines.get(2));
        p = Float.parseFloat(lines.get(3));

        rnd = new Random();
        rnd.setSeed(randomSeed);

        newSocket = new DatagramSocket(serverPortNo);
        int nThreads = Runtime.getRuntime().availableProcessors();
        forkJoinPool = new ForkJoinPool(nThreads);

        get_request();
    }

    private void get_request() {
        try {
            int process = 0;
            while (true) {
                byte[] data = new byte[FileScanner.CHUNK_SIZE];
                DatagramPacket receivedDatagram = new DatagramPacket(data, data.length);

                newSocket.receive(receivedDatagram);
                System.out.println("------------------Start new process " + process);

                forkJoinPool.execute(new handler(process++, receivedDatagram));
                System.out.println("******************End of process");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class handler extends RecursiveAction {

        /**
         *
         */
        private static final long serialVersionUID = 1L;
        private int process;

        handler(int process, DatagramPacket receivedDatagram) throws IOException {
            this.process = process;
            transmissionCompleted.put(process, false);
            processWndSize.put(process, new Point(1, 0));
            Packet received_pckt = new Packet(receivedDatagram.getData());
            DatagramSocket socket = new DatagramSocket();
            sockets.put(this.process, socket);
            if (received_pckt.getType() == Packet.PacketType.REQ) {
                sendTransmissionStarted(receivedDatagram);
                System.out.println("Server: Recieved Request " + ",in process : " + process);
                send_file(receivedDatagram);
            }
            System.out.println("start class");
        }

        @Override
        protected void compute() {
            try {
                for (int i = 0; i < files.get(process).getSegmentsCount(); i++) {
                    System.out.println("In While");
                    byte[] data = new byte[FileScanner.CHUNK_SIZE];
                    DatagramPacket receivedDatagram = new DatagramPacket(data, data.length);

                    sockets.get(process).receive(receivedDatagram);

                    Thread data_handler = new Thread() {
                        public void run() {
                            try {
                                // System.out.println("Start Extract in Data");
                                extract_data(receivedDatagram);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    };
                    data_handler.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private synchronized void extract_data(DatagramPacket receivedDatagram) throws IOException {
            Packet received_pckt = new Packet(receivedDatagram.getData());
            if (received_pckt.getType() == Packet.PacketType.SIGNAL) {
                System.out.println("Server: Recieved Acknowledge " + ",in process : " + process);
                if (packetStates.get(process).get(received_pckt.getSeqNo()) == Packet.State.WAITING_RESPONSE) {
                    slideWindow(received_pckt.getSeqNo(), receivedDatagram);
                }

            }
        }

        private synchronized void slideWindow(int seqNo, DatagramPacket receivedDatagram) throws IOException {
            boolean increased = false;
            if (processWndSize.get(process).x + 1 <= maxSlidingWindowSize) {
                processWndSize.get(process).x += 1;
                increased = true;
            }

            if (countNoAckStates() + 1 == files.get(process).getSegmentsCount()) {
                sendTransmissionCompleted(receivedDatagram);
                return;
            } else {
                System.out.println("-----------------------------------------");
                System.out.println(processWndSize.get(process).getX());
                System.out.println(processWndSize.get(process).getY());
                System.out.println("-----------------------------------------");

                int start = getStartOfWindow();
                packetStates.get(process).set(seqNo, Packet.State.ACK_RECEIVED);
                if (start == seqNo) {
                    threeDupAckCnt = 0;
                    start = getStartOfWindow();
                    for (int i = 0; i < processWndSize.get(process).x && (i + start) < packetStates.get(process).size(); i++) {
                        if (packetStates.get(process).get(i + start) == Packet.State.NOT_SENT) {
                            processWndSize.get(process).y += 1;
                            sendSegment(i + start, receivedDatagram);
                        }
                    }
                } else if (increased && processWndSize.get(process).y < packetStates.get(process).size()) {
                    threeDupAckCnt++;
                    if (threeDupAckCnt == 3) {
                        System.out.println("Three Duplicate Ack has been occured");
                        processWndSize.get(process).x -= 1;
                        processWndSize.get(process).x /= 2;
                        threeDupAckCnt = 0;
                    } else {
                        sendSegment(processWndSize.get(process).y, receivedDatagram);
                        processWndSize.get(process).y += 1;
                    }
                }

                try {
                    writer.write(Double.toString(processWndSize.get(process).x) + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private synchronized int getStartOfWindow() {
            int start = -1;
            for (Packet.State state : packetStates.get(process)) {
                start++;
                if (state != Packet.State.ACK_RECEIVED) {
                    return start;
                }
            }
            return -1;
        }

        private synchronized int countNoAckStates() {
            int cnt = 0;
            for (Packet.State state : packetStates.get(process)) {
                if (state == Packet.State.ACK_RECEIVED) {
                    cnt++;
                }
            }
            return cnt;
        }

        private synchronized void sendTransmissionCompleted(DatagramPacket receivedDatagram) throws IOException {
            sendPacket(new Packet(Packet.PacketType.SIGNAL, 0, Packet.Signal.TRANSMISSION_COMPLETED),
                    receivedDatagram.getAddress(), receivedDatagram.getPort());
            transmissionCompleted.put(process, true);
        }

        private synchronized void sendTransmissionStarted(DatagramPacket receivedDatagram) throws IOException {
            sendPacket(new Packet(Packet.PacketType.SIGNAL, 0, Packet.Signal.STARTING_TRANSMISSION),
                    receivedDatagram.getAddress(), receivedDatagram.getPort());
        }

        private synchronized void send_file(DatagramPacket receivedDatagram) {
            Packet received_pckt = new Packet(receivedDatagram.getData());
            try {
                byte[] filename = (byte[]) received_pckt.getBody();
                file = new FileScanner(new String(filename));
                files.put(process, file);
                Vector<Packet.State> packets = new Vector<Packet.State>();
                System.out.println("Number of segments in file : " + files.get(process).getSegmentsCount()
                        + ",in process : " + process);
                for (int i = 0; i < files.get(process).getSegmentsCount(); i++) {
                    packets.add(Packet.State.NOT_SENT);
                }
                packetStates.put(process, packets);
                System.out.println("WindowSize :" + processWndSize.get(process).getX());
                for (int i = 0; i < processWndSize.get(process).getX()
                        && i < files.get(process).getSegmentsCount(); i++) {
                    processWndSize.get(process).y += 1;
                    System.out.println("Increasing y ");
                    sendSegment(i, receivedDatagram);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private synchronized void sendSegment(int segmentId, DatagramPacket receivedDatagram) {
            System.out.println("Sending Segment No: " + segmentId + ",to Process : " + process);
            if (transmissionCompleted.get(process)) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }
            Packet packet = new Packet(Packet.PacketType.DATA, segmentId, files.get(process).getSegment(segmentId));
            float randomProb = (float) (rnd.nextInt(100) / 100.0);
            System.out.println("Random Number :" + randomProb);
            if (randomProb > p) {
                try {
                    sendPacket(packet, receivedDatagram.getAddress(), receivedDatagram.getPort());

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (packetStates.get(process).get(segmentId) == Packet.State.NOT_SENT) {
                packetStates.get(process).set(segmentId, Packet.State.WAITING_RESPONSE);
            }
            startTimer(segmentId, receivedDatagram);
        }

        private synchronized void sendPacket(Packet pckt, InetAddress serverAddress, int serverPortNo)
                throws IOException {
            DatagramPacket sendPacket = new DatagramPacket((byte[])pckt.getData(), pckt.getData().length, serverAddress,
                    serverPortNo);
            sockets.get(process).send(sendPacket);
        }

        private synchronized void startTimer(int segmentId, DatagramPacket receivedDatagram) {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    System.out.println("timer resending packet : " + segmentId + ", to process" + process);
                    if (packetStates.get(process).get(segmentId) == Packet.State.WAITING_RESPONSE) {
                        System.out.println("Resending");
                        processWndSize.get(process).x = 1;
                        try {
                            writer.write(Double.toString(processWndSize.get(process).x) + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        sendSegment(segmentId, receivedDatagram);
                    }
                }
            }, timeOut);
        }
    }
}