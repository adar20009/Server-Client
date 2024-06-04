package bgu.spl.net.srv;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.ConcurrentHashMap;


public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections;
    private int connectionsCounter;

    public ConnectionsImpl() {
        activeConnections = new ConcurrentHashMap<>();
        connectionsCounter = 0;
    }

    public void connect(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    public void disconnect(int connectionId) {
        try{
            activeConnections.get(connectionId).close();
        } catch (IOException e) {}
        activeConnections.remove(connectionId);
    }

    public int getCounter() {
        return connectionsCounter;
    }

    public int getAndIncCounter() {
        return connectionsCounter++;
    }
}
