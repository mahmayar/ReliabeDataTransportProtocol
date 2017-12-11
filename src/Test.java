import java.io.IOException;

public class Test {

    public static void main(String[] args) {
        try {
            Thread server = new Thread () {
                public void run () {
                    try {
                        new SelectiveRepeatServer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
            };server.start();

            Thread client = new Thread () {
                public void run () {
                    try {
                        new Client();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                };
            };client.start();



        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}