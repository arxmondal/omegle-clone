package com.example;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) {
        Javalin.create(config -> {
            config.staticFiles.add("src/main/resources/public", Location.EXTERNAL);
            config.router.mount(router -> {
                router.ws("/api/matchmaking", MatchMaking::websocket);
            });
        }).start(7070);
    }
}