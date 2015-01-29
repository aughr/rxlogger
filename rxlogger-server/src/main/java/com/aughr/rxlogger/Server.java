package com.aughr.rxlogger;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
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
import rx.subjects.PublishSubject;

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
    private final PublishSubject<String> subject;

    public Server(int port) {
        this.server = RxNetty.newHttpServerBuilder(port, new Handler())
                .pipelineConfigurator(PipelineConfigurators.<ByteBuf>serveSseConfigurator())
//                .enableWireLogging(LogLevel.ERROR)
                .build();

        this.subject = PublishSubject.create();
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
            if (request.getHttpMethod() == HttpMethod.POST) {
                return request.getContent().flatMap(content -> {
                    subject.onNext(content.toString(Charset.forName("UTF-8")));
                    return Observable.empty();
                });
            }

            return subject.flatMap(value -> {
                System.err.println("writing");
                ByteBuf buffer = response.getAllocator().buffer().writeBytes(value.getBytes(Charset.forName("UTF-8")));
                return response.writeAndFlush(new ServerSentEvent(buffer));
            });
        }
    }
}
