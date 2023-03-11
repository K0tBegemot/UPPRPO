public class Main {
    public static void main(String[] args) {
        WebsiteServer server;
        try {
            server = WebsiteServer.createWebsiteServer("127.0.0.2", "10000");
        }catch(Exception e)
        {
            System.err.println(e.getMessage());
            return;
        }
        server.start();
    }
}