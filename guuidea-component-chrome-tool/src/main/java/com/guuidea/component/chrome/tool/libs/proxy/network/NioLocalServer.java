
package com.guuidea.component.chrome.tool.libs.proxy.network;


import static com.guuidea.component.chrome.tool.common.Constant.APP_NAME;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.InvalidAlgorithmParameterException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.guuidea.component.chrome.tool.common.Constant;
import com.guuidea.component.chrome.tool.libs.proxy.network.nio.ChangeRequest;
import com.guuidea.component.chrome.tool.libs.proxy.network.nio.PipeWorker;
import com.guuidea.component.chrome.tool.libs.proxy.network.nio.RemoteSocketHandler;
import com.guuidea.component.chrome.tool.libs.proxy.network.nio.SocketHandlerBase;
import com.guuidea.component.chrome.tool.libs.proxy.utils.Util;
import com.guuidea.component.chrome.tool.model.ProxyAccount;


/**
 * Non-blocking local server for shadowsocks
 */
public class NioLocalServer extends SocketHandlerBase {
    private Logger logger = LoggerFactory.getLogger(NioLocalServer.class);

    private ServerSocketChannel _serverChannel;
    private RemoteSocketHandler _remoteSocketHandler;
    private ExecutorService _executor;

    public NioLocalServer(ProxyAccount proxyAccount) throws IOException, InvalidAlgorithmParameterException {
        super(proxyAccount);
        _executor = Executors.newCachedThreadPool();

        // init remote socket handler
        _remoteSocketHandler = new RemoteSocketHandler(proxyAccount);
        _executor.execute(_remoteSocketHandler);

        // print server info
        logger.info(APP_NAME + " v" + Constant.VERSION);
        logger.info("加密方式: " + proxyAccount.getMethod());
        logger.info(proxyAccount.getProxyType() + " 监听本地端口号: " + proxyAccount.getLocalPort());
    }

    @Override
    protected Selector initSelector() throws IOException {
        Selector socketSelector = SelectorProvider.provider().openSelector();
        _serverChannel = ServerSocketChannel.open();
        _serverChannel.configureBlocking(false);
        InetSocketAddress isa = new InetSocketAddress(proxyAccount.getLocalIpAddress(), proxyAccount.getLocalPort());
        _serverChannel.socket().bind(isa);
        _serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

        return socketSelector;
    }

    @Override
    protected boolean processPendingRequest(ChangeRequest request) {
        switch (request.type) {
            case ChangeRequest.CHANGE_SOCKET_OP:
                SelectionKey key = request.socket.keyFor(_selector);
                if ((key != null) && key.isValid()) {
                    key.interestOps(request.op);
                } else {
                    logger.warn("NioLocalServer::processPendingRequest (drop): {}", (""
                            + key + request.socket));
                }
                break;
            case ChangeRequest.CLOSE_CHANNEL:
                cleanUp(request.socket);
                break;
        }
        return true;
    }

    @Override
    protected void processSelect(SelectionKey key) {
        // Handle event
        try {
            if (key.isAcceptable()) {
                accept(key);
            } else if (key.isReadable()) {
                read(key);
            } else if (key.isWritable()) {
                write(key);
            }
        } catch (IOException e) {
            cleanUp((SocketChannel) key.channel());
        }
    }

    private void accept(SelectionKey key) throws IOException {
        // local socket established
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(_selector, SelectionKey.OP_READ);

        // prepare local socket write queue
        createWriteBuffer(socketChannel);

        // create pipe between local and remote socket
        PipeWorker pipe = _remoteSocketHandler.createPipe(this, socketChannel, proxyAccount.getRemoteIpAddress(), proxyAccount.getRemotePort());
        _pipes.put(socketChannel, pipe);
        _executor.execute(pipe);
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        int readCount;
        PipeWorker pipe = _pipes.get(socketChannel);
        byte[] data;

        if (pipe == null) {
            // should not happen
            cleanUp(socketChannel);
            return;
        }

        _readBuffer.clear();
        try {
            readCount = socketChannel.read(_readBuffer);
        } catch (IOException e) {
            cleanUp(socketChannel);
            return;
        }

        if (readCount == -1) {
            cleanUp(socketChannel);
            return;
        }

        data = _readBuffer.array();
        pipe.processData(data, readCount, true);
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        List queue = (List) _pendingData.get(socketChannel);
        if (queue != null) {
            synchronized (queue) {
                // Write data
                while (!queue.isEmpty()) {
                    ByteBuffer buf = (ByteBuffer) queue.get(0);
                    socketChannel.write(buf);
                    if (buf.remaining() > 0) {
                        break;
                    }
                    queue.remove(0);
                }

                if (queue.isEmpty()) {
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
        } else {
            logger.warn("LocalSocket::write queue = null: {}", socketChannel);
            return;
        }
    }

    @Override
    protected void cleanUp(SocketChannel socketChannel) {
        //logger.warning("LocalSocket closed: " + socketChannel);
        super.cleanUp(socketChannel);

        PipeWorker pipe = _pipes.get(socketChannel);
        if (pipe != null) {
            pipe.close();
            _pipes.remove(socketChannel);
            logger.info("LocalSocket closed: {}", pipe.socketInfo);
        } else {
            logger.info("LocalSocket closed (NULL): {} ", socketChannel);
        }

    }

    @Override
    public void close() {
        super.close();
        _executor.shutdownNow();

        try {
            _serverChannel.close();
            _remoteSocketHandler.close();
        } catch (IOException e) {
            logger.warn(Util.getErrorMessage(e));
        }
        logger.info("Server closed.");
    }
}
