package com.aughr.rxlogger;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.sse.ServerSentEvent;
import rx.Observable;

import java.util.Arrays;

public class Client {
    public static void main(String[] args) throws Exception {
        final Client client = new Client("localhost", 8080, args);
        System.err.println("starting");
        client.read().toBlocking().forEach(value -> System.err.printf("got %s\n", value));
    }

    private final HttpClient<ByteBuf, ServerSentEvent> httpClient;
    private final String[] filters;
    public Client(String host, int port, String[] filters) {
        this.httpClient = RxNetty.<ByteBuf, ServerSentEvent>newHttpClientBuilder(host, port)
                .pipelineConfigurator(PipelineConfigurators.<ByteBuf>clientSseConfigurator())
                .build();
        this.filters = filters;
    }

    public Observable<String> read() {
        QueryStringEncoder queryStringEncoder = new QueryStringEncoder("/");
        Arrays.stream(filters).forEach(f -> queryStringEncoder.addParam("filter", f));
        return httpClient.submit(HttpClientRequest.createGet(queryStringEncoder.toString()))
                .flatMap(response -> {
                    if (response.getStatus().equals(HttpResponseStatus.OK)) {
                        return response.getContent();
                    }
                    return Observable.error(new IllegalStateException("server returned status " + response.getStatus()));
                })
                .map(ServerSentEvent::contentAsString);
    }
}
