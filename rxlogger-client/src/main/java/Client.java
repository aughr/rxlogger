import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.sse.ServerSentEvent;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

public class Client {
    public static void main(String[] args) throws Exception {
        final Client client = new Client("localhost", 8080);
        System.err.println("starting");
        client.read().toBlocking().forEach(new Action1<Long>() {
            @Override
            public void call(Long value) {
                System.err.printf("got %s\n", value);
            }
        });
    }

    private final HttpClient<ByteBuf, ServerSentEvent> httpClient;
    public Client(String host, int port) {
        this.httpClient = RxNetty.<ByteBuf, ServerSentEvent>newHttpClientBuilder(host, port)
                .pipelineConfigurator(PipelineConfigurators.<ByteBuf>clientSseConfigurator())
                .build();
    }

    public Observable<Long> read() {
        return httpClient.submit(HttpClientRequest.createGet("/"))
                .flatMap(new Func1<HttpClientResponse<ServerSentEvent>, Observable<ServerSentEvent>>() {
                    @Override
                    public Observable<ServerSentEvent> call(HttpClientResponse<ServerSentEvent> response) {
                        if (response.getStatus().equals(HttpResponseStatus.OK)) {
                            return response.getContent();
                        }
                        return Observable.error(new IllegalStateException("server returned status " + response.getStatus()));
                    }
                })
                .map(new Func1<ServerSentEvent, Long>() {
                    @Override
                    public Long call(ServerSentEvent serverSentEvent) {
                        String value = serverSentEvent.contentAsString();
                        return Long.parseLong(value);
                    }
                });
    }
}
