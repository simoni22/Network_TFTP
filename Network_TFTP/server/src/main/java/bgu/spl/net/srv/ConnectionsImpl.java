package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;



public class ConnectionsImpl<T> implements Connections<T> {
    public ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> connections;


    public ConnectionsImpl() {
        connections = new ConcurrentHashMap<>();
    }
    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        // TODO implement this
        connections.put(connectionId, (BlockingConnectionHandler<T>) handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        // TODO implement this
        ConnectionHandler<T> handler = connections.get(connectionId);
        synchronized (handler) {
            handler.send(msg);
        }
        return true;
    }

    @Override
    public void disconnect(int connectionId) {
        try {
            connections.remove(connectionId).close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
