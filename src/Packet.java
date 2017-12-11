import java.nio.ByteBuffer;
import java.util.Arrays;

public class Packet implements Comparable<Packet> {
    private short len;
    private PacketType type;
    private int seqno;
    private byte[] data;
    private Object body;
    private int checkSum = 0 ;
    public static final int HEADER_LENGTH = 12;

    public Packet(PacketType type, int seqno, Object body) {
        encode(type, seqno, body);
    }

    public Packet(byte[] data) {
        decode(data);
    }

    private void encode(PacketType type, int seqno, Object body) {
        // get length, and data
        len = HEADER_LENGTH;
        byte[] data = null;
        switch (type) {
            case DATA:
                data = (byte[]) body;
                len += data.length;
                this.body = body;
//
//                if (seqno == 1)
//                    checkSum = ~getSum() + 70;
//                else
                    checkSum = ~getSum();
                break;
            case SIGNAL:
                int signalByteLength = 2; // Signal values are represented in shorts
                // (2 bytes).
                len += signalByteLength;
                Signal signal = (Signal) body;
                ByteBuffer byteBuffer = ByteBuffer.allocate(signalByteLength);
                byteBuffer.putShort((short) signal.ordinal());
                data = byteBuffer.array();
                break;
            case REQ:
                data = (byte[]) body;
                len += data.length;
                break;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(len);
        byteBuffer.putShort(len);
        byteBuffer.putShort((short) type.ordinal());
        byteBuffer.putInt(seqno);
        this.type = type;
        this.seqno = seqno;
        this.body = body;
        byteBuffer.putInt(checkSum);
        byteBuffer.put(data);
        this.data = byteBuffer.array();
    }

    private void decode(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);

        len = bb.getShort();

        type = PacketType.values()[bb.getShort()];

        seqno = bb.getInt();

        checkSum = bb.getInt();

        switch (type) {
            case DATA:

                body = Arrays.copyOfRange(data, HEADER_LENGTH, len);
                break;
            case SIGNAL:
                body = Signal.values()[(ByteBuffer.wrap(data, HEADER_LENGTH, 2).getShort())];
                break;
            case REQ:
                body = Arrays.copyOfRange(data, HEADER_LENGTH, len);
                break;
        }
    }

    public short getLength() {
        return len;
    }

    public PacketType getType() {
        return type;
    }

    public int getSeqNo() {
        return seqno;
    }

    public Object getBody() {
        return body;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        switch (type) {
            case DATA:
                return new String((byte[]) body);
            case SIGNAL:
                return ((Signal) body).toString();
            case REQ:
                return new String((byte[]) body);
        }
        return null;
    }

    public enum PacketType {
        SIGNAL, DATA, REQ
    }

    public enum Signal {
        TRANSMISSION_COMPLETED, RECEIVED_PACKET_ACK, STARTING_TRANSMISSION
    }

    public enum State {
        NOT_SENT, WAITING_RESPONSE, ACK_RECEIVED
    }

    @Override
    public int compareTo(Packet o) {
        return this.seqno - o.seqno;
    }

    public int getActualCheckSum() {
        return this.checkSum;
    }

    public int getSum() {
        int sum = 0;
        for (byte b : (byte[]) body) {
            sum += (0xff & b);
        }
        return sum;
    }
}