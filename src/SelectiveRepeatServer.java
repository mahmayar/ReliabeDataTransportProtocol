public class SelectiveRepeatServer {
    public SelectiveRepeatServer() {
        try {
            new Server("SelectiveRepeatServer.in");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}