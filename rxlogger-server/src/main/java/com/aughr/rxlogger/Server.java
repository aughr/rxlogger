package com.aughr.rxlogger;

import io.netty.buffer.ByteBuf;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.protocol.http.sse.ServerSentEvent;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;

import java.nio.charset.Charset;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Server {

    public static void main(String[] args) throws Exception {
        final Server server = new Server(8080);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    server.stop();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        server.start().join();
    }

    private final HttpServer<ByteBuf, ServerSentEvent> server;
    private final Observable<Long> data;

    public Server(int port) {
        this.server = RxNetty.newHttpServerBuilder(port, new Handler())
                .pipelineConfigurator(PipelineConfigurators.<ByteBuf>serveSseConfigurator())
//                .enableWireLogging(LogLevel.ERROR)
                .build();

        ConnectableObservable<Long> publish = Observable.interval(100, TimeUnit.MILLISECONDS).onBackpressureDrop().map(new Func1<Long, Long>() {
            @Override
            public Long call(Long aLong) {
                return new Random().nextLong();
            }
        }).publish();
        publish.connect();
        this.data = publish;

        data.forEach(new Action1<Long>() {
            @Override
            public void call(Long value) {
                System.out.println(String.valueOf(value));
            }
        });
    }

    public Server start() {
        server.start();
        return this;
    }

    public void join() throws InterruptedException {
        server.waitTillShutdown();
    }

    public void stop() throws InterruptedException {
        server.shutdown();
    }

    class Handler implements RequestHandler<ByteBuf, ServerSentEvent> {

        @Override
        public Observable<Void> handle(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ServerSentEvent> response) {
//            return Observable.interval(100, TimeUnit.MILLISECONDS)
//                    .flatMap(new Func1<Long, Observable<Void>>() {
//                        @Override
//                        public Observable<Void> call(Long interval) {
//                            ByteBuf eventId = response.getAllocator().buffer().writeLong(interval);
//                            ByteBuf data = response.getAllocator().buffer().writeLong(new Random().nextLong());
//                            return response.writeAndFlush(ServerSentEvent.withEventId(eventId, data));
//                        }
//                    });
            return data.flatMap(new Func1<Long, Observable<Void>>() {
                @Override
                public Observable<Void> call(Long value) {
                    System.err.println("writing");
                    ByteBuf buffer = response.getAllocator().buffer().writeBytes(String.valueOf(value).getBytes(Charset.forName("UTF-8")));
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    System.err.printf("writing %d bytes\n", buffer.writerIndex());
                    return response.writeAndFlush(new ServerSentEvent(buffer));
                }
            });
        }
    }
}
