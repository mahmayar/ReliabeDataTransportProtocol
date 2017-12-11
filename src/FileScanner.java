import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class FileScanner {
    public static final int CHUNK_SIZE = 1024;
    private byte[] data;
    private long length;
    private int segmentsCount;

    public FileScanner(String fileName) throws IOException {

        FileInputStream file = new FileInputStream(fileName);

        length = file.getChannel().size();
        data = new byte[(int) length];
        file.read(data, 0, (int) length);

        segmentsCount = (int) Math.ceil((double) data.length / CHUNK_SIZE);

    }

    public int getSegmentsCount() {
        return segmentsCount;
    }

    public boolean isExistedSegment(int segId) {
        if (segId < segmentsCount)
            return true;
        return false;
    }

    public byte[] getSegment(int segementId) {
        int end = Math.min(data.length, CHUNK_SIZE * (segementId + 1));
        return Arrays.copyOfRange(data, segementId * CHUNK_SIZE, end);
    }

}