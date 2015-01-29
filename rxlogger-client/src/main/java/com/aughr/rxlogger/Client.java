package com.aughr.rxlogger;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.sse.ServerSentEvent;
import rx.Observable;

public class Client {
    public static void main(String[] args) throws Exception {
        final Client client = new Client("localhost", 8080);
        System.err.println("starting");
        client.read().toBlocking().forEach(value -> System.err.printf("got %s\n", value));
    }

    private final HttpClient<ByteBuf, ServerSentEvent> httpClient;
    public Client(String host, int port) {
        this.httpClient = RxNetty.<ByteBuf, ServerSentEvent>newHttpClientBuilder(host, port)
                .pipelineConfigurator(PipelineConfigurators.<ByteBuf>clientSseConfigurator())
                .build();
    }

    public Observable<String> read() {
        return httpClient.submit(HttpClientRequest.createGet("/"))
                .flatMap(response -> {
                    if (response.getStatus().equals(HttpResponseStatus.OK)) {
                        return response.getContent();
                    }
                    return Observable.error(new IllegalStateException("server returned status " + response.getStatus()));
                })
                .map(ServerSentEvent::contentAsString);
    }
}
