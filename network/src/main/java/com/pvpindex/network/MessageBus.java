package com.pvpindex.network;

import java.util.function.Consumer;

public interface MessageBus {

    void connect() throws Exception;

    void disconnect();

    boolean isConnected();

    void publish(NetworkMessage message);

    void subscribe(NetworkMessageType type, Consumer<NetworkMessage> handler);

    void unsubscribeAll();
}
